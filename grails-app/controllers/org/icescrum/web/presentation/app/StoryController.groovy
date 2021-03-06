/*
 * Copyright (c) 2011 Kagilum.
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.web.presentation.app

import org.icescrum.core.domain.Story

import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.User
import grails.converters.JSON
import grails.plugin.springcache.annotations.Cacheable
import grails.plugins.springsecurity.Secured
import org.grails.followable.FollowException
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState

class StoryController {

    def storyService
    def featureService
    def springSecurityService

    @Secured('inProduct()')
    def show = {
        withStory { Story story ->
            withFormat {
                html { render status: 200, contentType: 'application/json', text: story as JSON }
                json { renderRESTJSON(text:story) }
                xml  { renderRESTXML(text:story) }
            }
        }
    }

    // TODO choose between show and index (also choose between list and index in other places)
    def index = {
        forward(action: 'show', params: params)
    }

    @Secured('isAuthenticated() and !archivedProduct()')
    def save = {
        if (!params.story){
            returnError(text:message(code:'todo.is.ui.no.data'))
            return
        }
        if (templates[params.template]){
            params.story << templates[params.template]
        }
        if (params.story.feature?.id == '') {
            params.story.'feature.id' = 'null'
        } else if (params.story.feature?.id) {
            params.story.'feature.id' = params.story.feature.id
        }
        //For REST XML..
        params.story.remove('feature')

        if (params.story.dependsOn?.id == '') {
            params.story.'dependsOn.id' = 'null'
        } else if (params.story.dependsOn?.id) {
            params.story.'dependsOn.id' = params.story.dependsOn.id
        }
        //For REST XML..
        params.story.remove('dependsOn')

        def story = new Story()
        try {
            Story.withTransaction {
                bindData(story, this.params, [include:['name','description','notes','type','affectVersion','feature','dependsOn']], "story")
                def user = (User) springSecurityService.currentUser
                def product = Product.get(params.long('product'))
                storyService.save(story, product, user)
                story.tags = params.story.tags instanceof String ? params.story.tags.split(',') : (params.story.tags instanceof String[] || params.story.tags instanceof List) ? params.story.tags : null
                entry.hook(id:"${controllerName}-${actionName}", model:[story:story])
                withFormat {
                    html { render status: 200, contentType: 'application/json', text: story as JSON }
                    json { renderRESTJSON(text:story, status:201) }
                    xml  { renderRESTXML(text:story, status:201) }
                }
            }

        } catch (RuntimeException e) {
            returnError(object:story, exception:e)
        }
    }

    @Secured('isAuthenticated() and !archivedProduct()')
    def update = {
        withStories{ List<Story> stories ->

            if (!params.story){
                returnError(text:message(code:'todo.is.ui.no.data'))
                return
            }

            stories.each { Story story ->
                if (!story.canUpdate(request.productOwner, springSecurityService.currentUser)) {
                    render(status: 403)
                    return
                }

                if ((request.format == 'xml' && params.story.feature == '') || params.story.feature?.id == '') {
                    params.story.'feature.id' = 'null'
                } else if (params.story.feature?.id) {
                    params.story.'feature.id' = params.story.feature.id
                }
                params.story.remove('feature') //For REST XML..

                if ((request.format == 'xml' && params.story.dependsOn == '') || params.story.dependsOn?.id == ''){
                    params.story.'dependsOn.id' = 'null'
                } else if (params.story.dependsOn?.id) {
                    params.story.'dependsOn.id' = params.story.dependsOn.id
                }
                params.story.remove('dependsOn') //For REST XML..

                Map props = [:]
                if (params.story.rank != null) {
                    props.rank = params.story.rank instanceof Number ? params.story.rank : params.story.rank.toInteger()
                }
                if (params.story.state != null) {
                    props.state = params.story.state instanceof Number ? params.story.state : params.story.state.toInteger()
                }
                if (params.story.effort != null) {
                    if (params.story.effort instanceof String) {
                        def effort = params.story.effort.replaceAll(',', '.')
                        props.effort = effort.isBigDecimal() ? effort.toBigDecimal() : effort // can be a "?"
                    } else {
                        props.effort = params.story.effort
                    }
                }
                Story.withTransaction {
                    if (params.story.tags != null) {
                        story.tags = params.story.tags instanceof String ? params.story.tags.split(',') : (params.story.tags instanceof String[] || params.story.tags instanceof List) ? params.story.tags : null
                    }
                    //for rest support
                    if ((request.format == 'xml' && params.story.parentSprint == '') || params.story.parentSprint?.id == '') {
                        props.parentSprint = null
                    } else {
                        def sprintId = params.story.'parentSprint.id'?.toLong() ?: params.story.parentSprint?.id?.toLong()
                        if (sprintId != null && story.parentSprint?.id != sprintId) {
                            def sprint = Sprint.getInProduct(params.long('product'), sprintId).list()
                            if (sprint) {
                                props.parentSprint = sprint
                            } else {
                                returnError(text:message(code: 'is.sprint.error.not.exist'))
                                return
                            }
                        } else if (params.boolean('shiftToNext')) {
                            def nextSprint = Sprint.findByParentReleaseAndOrderNumber(story.parentSprint.parentRelease, story.parentSprint.orderNumber + 1) ?: Sprint.findByParentReleaseAndOrderNumber(Release.findByOrderNumberAndParentProduct(story.parentSprint.parentRelease.orderNumber + 1, story.parentSprint.parentProduct), 1)
                            if (nextSprint) {
                                props.parentSprint = nextSprint
                            } else {
                                returnError(text:message(code: 'is.sprint.error.not.exist'))
                                return
                            }
                        }
                    }
                    bindData(story, params, [include:['name','description','notes','type','affectVersion', 'feature', 'dependsOn']], "story")
                    storyService.update(story, props)
                }
            }

            withFormat {
                html { render status: 200, contentType: 'application/json', text: stories as JSON }
                // TODO find a proper solution for multiple elements (A rest API should probably not require to manipulate arrays of resources)
                json { renderRESTJSON(text: stories.first()) }
                xml  { renderRESTXML(text:stories.first()) }
            }
        }
    }

    @Secured('isAuthenticated()')
    def delete = {
        withStories{List<Story> stories ->
            storyService.delete(stories, true, params.reason? params.reason.replaceAll("(\r\n|\n)", "<br/>") :null)
            withFormat {
                html { render(status: 200)  }
                json { render(status: 204) }
                xml { render(status: 204) }
            }
        }
    }

    @Secured('stakeHolder() or inProduct()')
    @Cacheable(cache = 'storyCache', keyGenerator='storiesKeyGenerator')
    def list = {
        def currentProduct = Product.load(params.long('product'))
        def stories = Story.searchAllByTermOrTag(currentProduct.id, params.term).sort { Story story -> story.id }
        withFormat {
            html { render(status:200, text:stories as JSON, contentType: 'application/json') }
            json { renderRESTJSON(text:stories) }
            xml  { renderRESTXML(text:stories) }
        }
    }

    @Secured('inProduct() and !archivedProduct()')
    def copy = {
        withStories{ List<Story> stories ->
            def copiedStories = storyService.copy(stories)
            withFormat {
                html { render(status: 200, contentType: 'application/json', text: copiedStories as JSON) }
                json { renderRESTJSON(text:copiedStories, status: 201) }
                xml  { renderRESTXML(text:copiedStories, status: 201) }
            }
        }
    }

    @Secured('isAuthenticated()')
    def openDialogDelete = {
        def state = Story.getInProduct(params.long('product'), params.list('id').first().toLong()).list()?.state
        def dialog = g.render(template: 'dialogs/delete', model:[back: params.back ? params.back : state >= Story.STATE_ACCEPTED ? '#backlog' : '#sandbox'])
        render(status: 200, contentType: 'application/json', text: [dialog: dialog] as JSON)
    }

    @Secured('productOwner() and !archivedProduct()')
    def done = {
        withStory { Story story ->
            withFormat {
                html {
                    def testsNotSuccess = story.acceptanceTests.findAll { AcceptanceTest test -> test.stateEnum != AcceptanceTestState.SUCCESS }
                    if (testsNotSuccess.size() > 0 && !params.boolean('confirm')) {
                        def dialog = g.render(template: 'dialogs/confirmDone', model: [testsNotSuccess: testsNotSuccess.sort {it.uid}])
                        render(status: 200, contentType: 'application/json', text: [dialog: dialog] as JSON)
                        return
                    }
                    storyService.done(story)
                    render(status: 200, contentType: 'application/json', text: story as JSON)
                }
                json {
                    storyService.done(story)
                    renderRESTJSON(text:story)
                }
                xml  {
                    storyService.done(story)
                    renderRESTXML(text:story)
                }
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def unDone = {
        withStory {Story story ->
            storyService.unDone(story)
            withFormat {
                html { render(status: 200, contentType: 'application/json', text: story as JSON)  }
                json { renderRESTJSON(text:story) }
                xml  { renderRESTXML(text:story) }
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def acceptAsFeature = {
        withStories{List<Story> stories ->
            stories = stories.reverse()
            def features = storyService.acceptToFeature(stories)
            //case one story & d&d from sandbox to backlog
            if (params.rank?.isInteger()){
                Feature feature = (Feature) features.first()
                feature.rank = params.int('rank')
                featureService.update(feature)
            }
            withFormat {
                html { render status: 200, contentType: 'application/json', text: features as JSON }
                json { renderRESTJSON(text:features) }
                xml  { renderRESTXML(text:features) }
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def acceptAsTask = {
        withStories{List<Story> stories ->
            stories = stories.reverse()
            def elements = storyService.acceptToUrgentTask(stories)
            withFormat {
                html { render status: 200, contentType: 'application/json', text: elements as JSON }
                json { renderRESTJSON(text:elements) }
                xml  { renderRESTXML(text:elements) }
            }
        }
    }

    @Secured('isAuthenticated() and !archivedProduct()')
    @Cacheable(cache = 'storyCache', keyGenerator='storiesKeyGenerator')
    def findDuplicate = {
        def stories = null
        withProduct{ product ->
            def terms = params.term?.tokenize()?.findAll{ it.size() >= 5 }
            if(terms){
                stories = Story.search(product.id, [term:terms,list:[max:3]]).collect {
                    "<a class='scrum-link' href='${createLink(absolute: true, mapping: "shortURL", params: [product: product.pkey], id: it.uid, title:it.description)}'>${it.name}</a>"
                }
            }
            render(status:200, text: stories ? "${message(code:'is.ui.story.duplicate')} ${stories.join(" or ")}" : "")
        }
    }

    def shortURL = {
        withProduct{ Product product ->
            if (!springSecurityService.isLoggedIn() && product.preferences.hidden){
                redirect(url:createLink(controller:'login', action: 'auth')+'?ref='+is.createScrumLink(controller: 'story', params:[uid: params.id]))
                return
            }
            redirect(url: is.createScrumLink(controller: 'story', params:[uid: params.id]))
        }
    }

    @Secured('stakeHolder()')
    def activities = {
        withStory { Story story ->
            withFormat {
                html { render(status: 200, contentType: 'application/json', text: story.activity as JSON) }
                json { renderRESTJSON(text:story.activity) }
                xml  { renderRESTXML(text:story.activity) }
            }
        }
    }

    @Secured('isAuthenticated() and !archivedProduct()')
    def like = {
        withStory { Story story ->
            try {
                User user = springSecurityService.currentUser
                def like = story.likers?.contains(user)
                def result = [status: params.boolean('status') ? like : !like]
                if (!params.boolean('status')){
                    like ? story.removeFromLikers(user) : story.addToLikers(user)
                    //fake update //todo allow customDirty properties
                    story.lastUpdated = new Date()
                    storyService.update(story, [likers:''])
                }
                result.likers = story.likers.size()
                withFormat {
                    html {
                        result.likers += " ${message(code: 'is.likers', args: [result.likers > 1 ? 's' : ''])}"
                        render(status: 200, contentType: 'application/json', text: result as JSON)
                    }
                    json { renderRESTJSON(text:result) }
                    xml  { renderRESTXML(text:result) }
                }
            } catch (FollowException e) {
                returnError(e)
            }
        }
    }

    @Secured('isAuthenticated() and !archivedProduct()')
    def follow = {
        withStory { Story story ->
            try {
                User user = springSecurityService.currentUser
                def follow = story.followers?.contains(user)
                def result = [status: params.boolean('status') ? follow : !follow]
                if (!params.boolean('status')){
                    follow ? story.removeLink(user.id) : story.addFollower(user)
                    //fake update //todo allow customDirty properties
                    story.lastUpdated = new Date()
                    storyService.update(story, [followers:''])
                }
                result.followers = story.totalFollowers
                withFormat {
                    html {
                        result.followers += " ${message(code: 'is.followable.followers', args: [result.followers > 1 ? 's' : ''])}"
                        render(status: 200, contentType: 'application/json', text: result as JSON)
                    }
                    json { renderRESTJSON(text:result) }
                    xml  { renderRESTXML(text:result) }
                }
            } catch (FollowException e) {
                returnError(e)
            }
        }
    }

    @Secured('stakeHolder() or inProduct()')
    @Cacheable(cache = 'storyCache', keyGenerator='storiesKeyGenerator')
    def dependenceEntries = {
        withStory { story ->
            def stories = Story.findPossiblesDependences(story).list()?.sort{ a -> a.feature == story.feature ? 0 : 1}
            def storyEntries = stories.collect { [id: it.id, text: it.name + ' (' + it.uid + ')'] }
            if (params.term) {
                storyEntries = storyEntries.findAll { it.text.contains(params.term) }
            }
            render status: 200, contentType: 'application/json', text: storyEntries as JSON
        }
    }

    //TODO replace with real action to save template
    def templateEntries = {
        if (params.template){
            render text:templates[params.template] as JSON, contentType: 'application/json', status:200
        } else {
            render text:templates.collect{ key, val -> [id:key, text:key]} as JSON, contentType: 'application/json', status:200
        }
    }

    //TODO replace with real action to save template
    static templates = ['Functional template':[tags:'tag1,tag2,tag3',
            description:'this is a description  for template 1',
            notes:'Custom notes from a template',
            feature:[id:2, name:'La feature 2'],
            id:666],
            'Defect template':[tags:'tag1,tag2,tag3',
                    type:2,
                    description:'this is a description for template 2',
                    notes:'Custom notes from a template',
                    feature:[id:3, name:'La feature 3'], dependsOn:[id:5,uid:5]],
            'Other template':[type:3,
                    description:'this is a description for template 3',
                    notes:'Custom notes from a template',
                    dependsOn:[id:16, uid:16]]]
}
