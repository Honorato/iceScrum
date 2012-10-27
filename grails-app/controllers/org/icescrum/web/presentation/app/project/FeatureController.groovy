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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 *
 */
package org.icescrum.web.presentation.app.project

import grails.converters.JSON
import grails.plugin.springcache.annotations.Cacheable
import grails.plugins.springsecurity.Secured
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.PlanningPokerGame
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Story
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.utils.BundleUtils
import org.icescrum.plugins.attachmentable.interfaces.AttachmentException

import java.text.DecimalFormat

@Secured('inProduct()')
class FeatureController {

    def featureService
    def springSecurityService

    @Secured('productOwner() and !archivedProduct()')
    def save(long product) {
        def feature = new Feature()
        bindData(feature, this.params, [include:['name','description','notes','color','type','value']], "feature")

        try {
            featureService.save(feature, Product.get(product))
            feature.tags = params.feature.tags instanceof String ? params.feature.tags.split(',') : (params.feature.tags instanceof String[] || params.feature.tags instanceof List) ? params.feature.tags : null
            this.manageAttachments(feature)
            entry.hook(id:"${controllerName}-${actionName}", model:[feature:feature])
            withFormat {
                html { render status: 200, contentType: 'application/json', text: feature as JSON }
                json { renderRESTJSON(text:feature, status:201) }
                xml  { renderRESTXML(text:feature, status:201) }
            }
        } catch (RuntimeException e) {
                returnError(exception:e, object:feature)
        } catch (AttachmentException e) {
            returnError(exception:e)
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def update(long product, boolean table, String name) {
        withFeature{ Feature feature ->
             // If the version is different, the feature has been modified since the last loading
            if (params.feature.version && params.long('feature.version') != feature.version) {
                returnError(text:message(code: 'is.stale.object', args: [message(code: 'is.feature')]))
                return
            }

            def successRank = true

            if (params.int('feature.rank') && feature.rank != params.int('feature.rank')) {
                if (!featureService.rank(feature, params.int('feature.rank'))) {
                    successRank = false
                }
            }

            if (successRank) {
                if(!feature.color.equals(params.feature.color)) {
                    feature.stories*.lastUpdated = new Date()
                }

                bindData(feature, this.params, [include:['name','description','notes','color','type','value']], "feature")
                feature.tags = params.feature.tags instanceof String ? params.feature.tags.split(',') : (params.feature.tags instanceof String[] || params.feature.tags instanceof List) ? params.feature.tags : null

                this.manageAttachments(feature)
                featureService.update(feature)

                if (table) {
                    def returnValue
                    if (name == 'type')
                        returnValue = message(code: BundleUtils.featureTypes[feature.type])
                    else if (name == 'description') {
                        returnValue = feature.description?.encodeAsHTML()?.encodeAsNL2BR()
                    }
                    else
                        returnValue = feature[name].encodeAsHTML()

                    def version = feature.isDirty() ? feature.version + 1 : feature.version
                    render(status: 200, text: [version: version, value: returnValue ?: '', object: feature] as JSON)
                    return
                }
                def next = null
                if (params.continue) {
                    next = Feature.findByBacklogAndRank(feature.backlog, feature.rank + 1, [cache: true])
                }
                entry.hook(id:"${controllerName}-${actionName}", model:[feature:feature])
                withFormat {
                    html { render status: 200, contentType: 'application/json', text: [feature: feature, next: next?.id ?: null] as JSON }
                    json { renderRESTJSON(text:feature) }
                    xml  { renderRESTXML(text:feature) }
                }
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def delete() {
        withFeatures{ List<Feature> features ->
            features.each { feature ->
                featureService.delete(feature)
            }
            def ids = []
            params.list('id').each { ids << [id: it] }
            withFormat {
                html { render status: 200, contentType: 'application/json', text: ids as JSON }
                json { render status: 204, contentType: 'application/json', text: '' }
                xml { render status: 204, contentType: 'text/xml', text: '' }
            }
        }
    }

    def list(long product, String term, String windowType, String viewType) {
        def features = (term && term != '') ? Feature.findAllByProductAndTerm(product, '%' + term + '%').list() : Feature.findAllByBacklog(Product.load(product), [cache: true, sort: 'rank'])
        withFormat{
            html {
                def template = windowType == 'widget' ? 'widget/widgetView' : viewType ? 'window/' + viewType : 'window/postitsView'

                def currentProduct = Product.get(product)
                def maxRank = Feature.countByBacklog(currentProduct)
                //Pour la vue tableau
                def rankSelect = ''
                maxRank.times { rankSelect += "'${it + 1}':'${it + 1}'" + (it < maxRank - 1 ? ',' : '') }
                def typeSelect = BundleUtils.featureTypes.collect {k, v -> "'$k':'${message(code: v)}'" }.join(',')
                def suiteSelect = "'?':'?',"

                def currentSuite = PlanningPokerGame.getInteger(PlanningPokerGame.INTEGER_SUITE)

                currentSuite = currentSuite.eachWithIndex { t, i ->
                    suiteSelect += "'${t}':'${t}'" + (i < currentSuite.size() - 1 ? ',' : '')
                }
                render(template: template, model: [features: features, typeSelect: typeSelect, rankSelect: rankSelect, suiteSelect: suiteSelect], params: [product: product])
            }
            json { renderRESTJSON(text:features) }
            xml  { renderRESTXML(text:features) }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def rank() {
        withFeature{ Feature feature ->
            Integer rank = params.feature.rank instanceof Number ? params.feature.rank : params.feature.rank.isNumber() ? params.feature.rank.toInteger() : null
            if (feature == null || rank == null) {
                returnError(text:message(code: 'is.feature.rank.error'))
            }
            if (featureService.rank(feature, rank)) {
               withFormat {
                    html { render status: 200, text:'success' }
                    json { renderRESTJSON(text:feature) }
                    xml  { renderRESTXML(text:feature) }
                }
            } else {
                returnError(text:message(code: 'is.feature.rank.error'))
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def add() {
        def valuesList = PlanningPokerGame.getInteger(PlanningPokerGame.INTEGER_SUITE)
        render(template: 'window/manage', model: [valuesList: valuesList,
                colorsLabels: BundleUtils.colorsSelect.values().collect { message(code: it) },
                colorsKeys: BundleUtils.colorsSelect.keySet().asList(),
                typesNames: BundleUtils.featureTypes.values().collect {v -> message(code: v)},
                typesId: BundleUtils.featureTypes.keySet().asList()
        ])
    }

    @Secured('productOwner() and !archivedProduct()')
    def edit() {
        withFeature{ Feature feature ->
            Product product = (Product) feature.backlog
            def valuesList = PlanningPokerGame.getInteger(PlanningPokerGame.INTEGER_SUITE)

            def rankList = []
            def maxRank = Feature.countByBacklog(product)
            maxRank.times { rankList << (it + 1) }

            def next = Feature.findByBacklogAndRank(product, feature.rank + 1, [cache: true])
            render(template: 'window/manage', model: [valuesList: valuesList,
                    rankList: rankList,
                    next: next?.id ?: '',
                    colorsLabels: BundleUtils.colorsSelect.values().collect { message(code: it) },
                    colorsKeys: BundleUtils.colorsSelect.keySet().asList(),
                    feature: feature,
                    typesNames: BundleUtils.featureTypes.values().collect {v -> message(code: v)},
                    typesId: BundleUtils.featureTypes.keySet().asList()
            ])
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def copyFeatureToBacklog() {
        withFeature{ Feature feature ->
            def story = featureService.copyToBacklog(feature)
            withFormat {
                html { render status: 200, text:'success' }
                json { renderRESTJSON(text:story, status:201) }
                xml  { renderRESTXML(text:story, status:201) }
            }
        }
    }

    @Cacheable(cache = "projectCache", keyGenerator= 'featuresKeyGenerator')
    def productParkingLotChart(long product) {
        def currentProduct = Product.get(product)
        def values = featureService.productParkingLotValues(currentProduct)
        def indexF = 1
        def valueToDisplay = []
        values.value?.each {
            def value = []
            value << new DecimalFormat("#.##").format(it).toString()
            value << indexF
            valueToDisplay << value
            indexF++
        }
        if (valueToDisplay.size() > 0)
            render(template: 'charts/productParkinglot', model: [
                    withButtonBar: params.withButtonBar != null ? params.withButtonBar : true,
                    values: valueToDisplay as JSON,
                    featuresNames: values.label as JSON])
        else {
            returnError(text: message(code: 'is.chart.error.no.values'))
        }
    }

    def print(long product, String format, boolean get, boolean status) {
        def currentProduct = Product.get(product)
        def values = featureService.productParkingLotValues(currentProduct)
        def data = []
        if (!values) {
            returnError(text:message(code: 'is.report.error.no.data'))
            return
        } else if (get) {
            currentProduct.features.eachWithIndex { feature, index ->
                data << [
                        name: feature.name,
                        description: feature.description,
                        notes: feature.notes?.replaceAll(/<.*?>/, ''),
                        rank: feature.rank,
                        type: feature.type,
                        value: feature.value,
                        effort: feature.effort,
                        associatedStories: Story.countByFeature(feature),
                        associatedStoriesDone: feature.countDoneStories,
                        parkingLotValue: values[index].value
                ]
            }
            outputJasperReport('features', format, [[product: currentProduct.name, features: data ?: null]], currentProduct.name)
        } else if (status) {
            render(status: 200, contentType: 'application/json', text: session.progress as JSON)
        } else {
            session.progress = new ProgressSupport()
            def dialog = g.render(template: '/scrumOS/report')
            render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
        }
    }

    @Cacheable(cache = 'featureCache', keyGenerator='featureKeyGenerator')
    def index() {
        if (request?.format == 'html'){
            render(status:404)
            return
        }

        withFeature{ Feature feature ->
            withFormat {
                json { renderRESTJSON(text:feature) }
                xml { renderRESTXML(text:feature) }
            }
        }
    }

    def show() {
        redirect(action:'index', controller: controllerName, params:params)
    }

    private manageAttachments(def feature) {
        def user = springSecurityService.currentUser
        def needPush = false

        if (params.feature.attachments && feature.id && !params.feature.list('attachments') && feature.attachments*.id.size() > 0) {
            feature.removeAllAttachments()
            needPush = true
        } else if (feature.attachments*.id.size() > 0) {
            feature.attachments*.id.each {
                if (!params.feature.list('attachments').contains(it.toString()))
                    feature.removeAttachment(it)
                    needPush = true
            }
        }
        def uploadedFiles = []
        params.list('attachments')?.each { attachment ->
            "${attachment}".split(":").with {
                if (session.uploadedFiles[it[0]])
                    uploadedFiles << [file: new File((String) session.uploadedFiles[it[0]]), name: it[1]]
            }
        }
        if (uploadedFiles){
            feature.addAttachments(user, uploadedFiles)
            needPush = true
        }
        session.uploadedFiles = null

        if (needPush){
            feature.lastUpdated = new Date()
            broadcast(function: 'update', message: feature)
        }
    }

    def download(long id) {
        forward(action: 'download', controller: 'attachmentable', id: params.id)
        return
    }
}
