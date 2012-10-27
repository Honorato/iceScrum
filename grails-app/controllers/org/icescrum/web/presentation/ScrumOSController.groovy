/*
 * Copyright (c) 2010 iceScrum Technologies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 *
 */

package org.icescrum.web.presentation

import org.springframework.web.servlet.support.RequestContextUtils as RCU

import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.apache.commons.io.FilenameUtils
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import org.icescrum.core.support.ProgressSupport
import org.icescrum.web.upload.AjaxMultipartResolver
import org.springframework.mail.MailException
import org.icescrum.core.domain.Sprint
import grails.plugin.springcache.annotations.Cacheable
import org.springframework.security.acls.domain.BasePermission
import org.icescrum.core.domain.preferences.ProductPreferences
import org.codehaus.groovy.grails.plugins.DefaultGrailsPluginManager
import org.codehaus.groovy.grails.plugins.GrailsPlugin

class ScrumOSController {

    def springSecurityService
    def menuBarSupport
    def notificationEmailService
    def securityService
    def uiDefinitionService
    def grailsApplication

    def index(long product, String lang) {
        def currentUserInstance = null

        def locale = lang ?: null
        try {
            def localeAccept = request.getHeader("accept-language")?.split(",")
            if (localeAccept)
                localeAccept = localeAccept[0]?.split("-")

            if (localeAccept?.size() > 0) {
                locale = lang ?: localeAccept[0].toString()
            }
        } catch (Exception e) {}

        if (springSecurityService.isLoggedIn()) {
            currentUserInstance = User.get(springSecurityService.principal.id)
            if (locale != currentUserInstance.preferences.language || RCU.getLocale(request).toString() != currentUserInstance.preferences.language) {
                RCU.getLocaleResolver(request).setLocale(request, response, new Locale(currentUserInstance.preferences.language))
            }
        } else {
            if (locale) {
                RCU.getLocaleResolver(request).setLocale(request, response, new Locale(locale))
            }
        }
        def currentProductInstance = product ? Product.get(product) : null

        if (currentProductInstance?.preferences?.hidden && !securityService.inProduct(currentProductInstance, springSecurityService.authentication) && !securityService.stakeHolder(currentProductInstance,springSecurityService.authentication,false)){
            redirect(action:'error403',controller:'errors')
            return
        }

        if (currentProductInstance && currentUserInstance && !securityService.hasRoleAdmin(currentUserInstance) && currentUserInstance.preferences.lastProductOpened != currentProductInstance.pkey){
            currentUserInstance.preferences.lastProductOpened = currentProductInstance.pkey
            currentUserInstance.save()
        }
        //For PO / SM : WRITE - TM / SH : READ
        def products = currentUserInstance ? Product.findAllByRole(currentUserInstance, [BasePermission.WRITE,BasePermission.READ], [cache:true, max:11]) : []
        def pCount = products?.size()

        [user: currentUserInstance,
                lang: RCU.getLocale(request).toString().substring(0, 2),
                product: currentProductInstance,
                publicProductsExists: ProductPreferences.countByHidden(false,[cache:true]) ? true : false,
                productFilteredsListCount: pCount,
                productFilteredsList: pCount > 9 ? products?.subList(0,9) : products]
    }


    def openWidget(long product, String window) {
        if (!window) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.error.no.widget')]] as JSON)
            return
        }


        def uiRequested = window
        def uiDefinition = uiDefinitionService.getDefinitionById(uiRequested)
        if (uiDefinition) {
            def paramsWidget = null
            if (product) {
                paramsWidget = [product: product]
            }

            def url = createLink(controller: window, action: uiDefinition.widget?.init, params: paramsWidget).toString() - request.contextPath
            if (!menuBarSupport.permissionDynamicBar(url)) {
                render(status: 400)
                return
            }

            render is.widget([
                    id: window,
                    hasToolbar: uiDefinition.widget?.toolbar,
                    closeable: uiDefinition.widget?.closeable,
                    sortable: uiDefinition.widget?.sortable,
                    windowable: uiDefinition.window ? true : false,
                    resizable: uiDefinition.widget?.resizable ?: false,
                    hasTitleBarContent: uiDefinition.widget?.titleBarContent,
                    title: message(code: uiDefinition.widget?.title),
                    init: uiDefinition.widget?.init
            ], {})
        }
    }

    def openWindow(long product, String window, String viewType, String actionWindow) {
        if (!window) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.error.no.window')]] as JSON)
            return
        }
        viewType = viewType ?: springSecurityService.isLoggedIn() ? 'postitsView' : 'tableView'

        def uiRequested = window
        def uiDefinition = uiDefinitionService.getDefinitionById(uiRequested)
        if (uiDefinition) {

            def projectName
            def param = [:]
            if (product) {
                projectName = Product.get(product)?.name
                param = [product: product]
            }
            def url = createLink(controller: window, action: actionWindow ?: uiDefinition.window?.init, params: param).toString() - request.contextPath

            if (!menuBarSupport.permissionDynamicBar(url)){
                if (springSecurityService.isLoggedIn()){
                    render(status:403)
                } else {
                    render(status:401, contentType: 'application/json', text:[url:window ? '#'+ window + (actionWindow ? '/' + actionWindow : '') : ''] as JSON)
                }
                return
            }
            render is.window([
                    window: window,
                    projectName: projectName,
                    title: message(code: uiDefinition.window?.title),
                    help: message(code: uiDefinition.window?.help),
                    shortcuts: uiDefinition.shortcuts,
                    hasToolbar: uiDefinition.window?.toolbar,
                    hasTitleBarContent: uiDefinition.window?.titleBarContent,
                    maximizeable: uiDefinition.window?.maximizeable,
                    closeable: uiDefinition.window?.closeable,
                    widgetable: uiDefinition.widget ? true : false,
                    init: actionWindow ?: uiDefinition.window?.init
            ], {})
        }
    }

    def reloadToolbar(String window) {
        if (!window) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.error.no.window.toolbar')]] as JSON)
            return
        }
        def uiRequested = window
        def uiDefinition = uiDefinitionService.getDefinitionById(uiRequested)
        if (uiDefinition) {
            forward(controller: window, action: 'toolbar', params: params)
        }
    }

    @Secured('isAuthenticated()')
    def upload() {
        def upfile = request.getFile('file')
        def filename = FilenameUtils.getBaseName(upfile.originalFilename)
        def ext = FilenameUtils.getExtension(upfile.originalFilename)
        def tmpF = session.createTempFile(filename, '.' + ext)
        request.getFile("file").transferTo(tmpF)
        if (!session.uploadedFiles)
            session.uploadedFiles = [:]
        session.uploadedFiles["${params."X-Progress-ID"}"] = tmpF.toString()
        log.info "upload done for session: ${session?.id} / fileID: ${params."X-Progress-ID"}"
        render(status: 200)
    }

    @Secured('isAuthenticated()')
    def uploadStatus() {
        log.debug "upload status for session: ${session?.id} / fileID: ${params?."X-Progress-ID" ?: 'null'}"
        if (params."X-Progress-ID" && session[AjaxMultipartResolver.progressAttrName(params."X-Progress-ID")]) {
            if (((ProgressSupport) session[AjaxMultipartResolver.progressAttrName(params."X-Progress-ID")])?.complete) {
                render(status: 200, contentType: 'application/json', text: session[AjaxMultipartResolver.progressAttrName(params."X-Progress-ID")] as JSON)
                session.removeAttribute([AjaxMultipartResolver.progressAttrName(params."X-Progress-ID")])
            } else {
                render(status: 200, contentType: 'application/json', text: session[AjaxMultipartResolver.progressAttrName(params."X-Progress-ID")] as JSON)
            }
        } else {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.upload.error')]] as JSON)
        }
    }

    def about() {
        def locale = RCU.getLocale(request)
        def file = new File(grailsAttributes.getApplicationContext().getResource("/infos").getFile().toString() + File.separatorChar + "about_${locale}.xml")
        if (!file.exists()) {
            file = new File(grailsAttributes.getApplicationContext().getResource("/infos").getFile().toString() + File.separatorChar + "about_en.xml")
        }
        def aboutXml = new XmlSlurper().parse(file)
        def license
        def dialog = g.render(template: "about/index", model: [about: aboutXml])
        render(status: 200, contentType: 'application/json', text:[dialog:dialog] as JSON)
    }

    def textileParser(int truncate, String data, boolean withoutHeader) {
        if (truncate) {
            params.data = is.truncated([size: truncate], data)
        }
        if (withoutHeader) {
            render(text: wikitext.renderHtml([markup: "Textile"], data))
        } else {
            render(status: 200, template: 'textileParser')
        }
    }

    def reportError(String stackError, String comments) {
        try {
            notificationEmailService.send([
                    to: grailsApplication.config.icescrum.alerts.errors.to,
                    subject: "[iceScrum][report] Rapport d'erreur",
                    view: '/emails-templates/reportError',
                    model: [error: stackError,
                            comment: comments,
                            appID: grailsApplication.config.icescrum.appID,
                            ip: request.getHeader('X-Forwarded-For') ?: request.getRemoteAddr(),
                            date: g.formatDate(date: new Date(), formatName: 'is.date.format.short.time'),
                            version: g.meta(name: 'app.version')]
            ]);
            render(status: 200, contentType: 'application/json', text: [notice: [text: message(code: 'is.blame.sended'), type: 'notice']] as JSON)
        } catch (MailException e) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.mail.error')]] as JSON)
            return
        } catch (RuntimeException re) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: re.getMessage())]] as JSON)
            return
        } catch (Exception e) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.mail.error')]] as JSON)
            return
        }
    }

    @Cacheable(cache = 'projectCache', keyGenerator = 'projectUserKeyGenerator')
    def templates(long product) {
        def currentSprint = null
        def p = null
        if (product) {
            p = Product.get(product)
            currentSprint = Sprint.findCurrentSprint(p.id).list() ?: null
        }
        def tmpl = g.render(
                template: 'templatesJS',
                model: [id: controllerName,
                        currentSprint: currentSprint,
                        product:p
                ])

        tmpl = "${tmpl}".split("<div class='templates'>")
        tmpl[1] = tmpl[1].replaceAll('%3F', '?').replaceAll('%3D', '=').replaceAll('<script type="text/javascript">', '<js>').replaceAll('</script>', '</js>').replaceAll('<template ', '<script type="text/x-jqote-template" ').replaceAll('</template>', '</script>')
        render(text: tmpl[0] + '<div class="templates">' + tmpl[1], status: 200)
    }
}
