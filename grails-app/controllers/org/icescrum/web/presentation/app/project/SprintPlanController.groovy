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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Jeroen Broekhuizen (Jeroen.Broekhuizen@quintiq.com)
 *
 */

package org.icescrum.web.presentation.app.project

import grails.converters.JSON
import grails.plugin.springcache.annotations.Cacheable
import grails.plugins.springsecurity.Secured
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.utils.BundleUtils
import org.icescrum.core.domain.*

@Secured('inProduct()')
class SprintPlanController {

    def springSecurityService
    def storyService
    def sprintService
    def taskService
    def releaseService
    def userService

    def titleBarContent = {
        def currentProduct = Product.load(params.product)
        def sprint
        if (!params.id) {
            sprint = Sprint.findCurrentOrNextSprint(currentProduct.id).list()[0]
        } else {
            sprint = (Sprint)Sprint.getInProduct(params.long('product'),params.long('id')).list()
        }

        def sprintsName = []
        def sprintsId = []
        currentProduct.releases?.sort({a, b -> a.orderNumber <=> b.orderNumber} as Comparator)?.each {
            sprintsName.addAll(it.sprints.collect {v -> "${it.name} - Sprint ${v.orderNumber}"})
            sprintsId.addAll(it.sprints.id)
        }
        render(template: 'window/titleBarContent',
                model: [sprintsId: sprintsId,
                        sprint: sprint,
                        sprintsName: sprintsName])
    }

    def toolbar = {
        def sprint
        def currentProduct = Product.load(params.product)
        if (!params.id) {
            sprint = Sprint.findCurrentOrNextSprint(currentProduct.id).list()[0]
            if (sprint)
                params.id = sprint.id
        } else {
            sprint = Sprint.getInProduct(params.long('product'),params.long('id')).list()
        }

        User user = (User) springSecurityService.currentUser

        render(template: 'window/toolbar',
                model: [hideDoneState: user.preferences.hideDoneState,
                        currentFilter: user.preferences.filterTask,
                        sprint: sprint ?: null])
    }

    def index(long product, long id, long userid, String term, String viewType) {
        def sprint
        User user = (User) springSecurityService.currentUser
        def currentProduct = Product.load(product)
        if (!id) {
            sprint = Sprint.findCurrentOrNextSprint(currentProduct.id).list()[0]
            if (sprint)
                id = sprint.id
            else {
                def release = currentProduct.releases.find {it.state == Release.STATE_WAIT}
                render(template: 'window/blank', model: [release: release ?: null])
                return
            }
        }else{
            sprint = Sprint.getInProduct(product,id).list()
        }
        if (!sprint) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.sprint.not.exist')]] as JSON)
            return
        }

        def hideDoneState = """<span id="show-done-sprint-${sprint.parentRelease.id}-${sprint.orderNumber}" class="show-done-sprint ${sprint.state == Sprint.STATE_INPROGRESS ? '' : 'hidden'}">${
                                        is.link(action:"changeHideDoneState",
                                                controller:"${controllerName}",
                                                history:"false",
                                                remote:"true",
                                                onSuccess:"jQuery.icescrum.updateHideDoneState('${message(code: 'is.ui.sprintPlan.toolbar.showDoneState')}','${message(code: 'is.ui.sprintPlan.toolbar.hideDoneState')}');" ,
                                                id:"${id}",
                                                update:"window-content-${controllerName}",user.preferences.hideDoneState ? message(code: 'is.ui.sprintPlan.toolbar.showDoneState') : message(code: 'is.ui.sprintPlan.toolbar.hideDoneState'))
                                        } </span>"""

        def columns = [
                [key: (Task.STATE_WAIT), name: 'is.task.state.wait'],
                [key: (Task.STATE_BUSY), name: 'is.task.state.inprogress'],
                [key: (Task.STATE_DONE), name: 'is.task.state.done', html:hideDoneState]
        ]

        def stateSelect = BundleUtils.taskStates.collect {k, v -> "'$k':'${message(code: v)}'" }.join(',')

        def stories
        def recurrentTasks
        def urgentTasks

        if (term != '') {
            stories = Story.findStoriesFilter(sprint, '%' + term + '%', user, userid).listDistinct()
            recurrentTasks = Task.findRecurrentTasksFilter(sprint, '%' + term + '%', user, userid).listDistinct()
            urgentTasks = Task.findUrgentTasksFilter(sprint, '%' + term + '%', user, userid).listDistinct()
        } else {
            stories = Story.findStoriesFilter(sprint, null, user, userid).listDistinct()
            recurrentTasks = Task.findRecurrentTasksFilter(sprint, null, user, userid).listDistinct()
            urgentTasks = Task.findUrgentTasksFilter(sprint, null, user, userid).listDistinct()
        }

        def suiteSelect = ''
        def currentSuite = PlanningPokerGame.getInteger(currentProduct.planningPokerGameType)

        currentSuite = currentSuite.eachWithIndex { t, i ->
            suiteSelect += "'${t}':'${t}'" + (i < currentSuite.size() - 1 ? ',' : '')
        }
        def template = viewType ? 'window/' + viewType : 'window/postitsView'
        render(template: template,
                model: [sprint: sprint,
                        stories: stories.findAll{it.state < Story.STATE_DONE},
                        storiesDone: stories.findAll{it.state == Story.STATE_DONE},
                        recurrentTasks: recurrentTasks,
                        urgentTasks: urgentTasks,
                        columns: columns,
                        stateSelect: stateSelect,
                        suiteSelect: suiteSelect,
                        previousSprintExist: (sprint.orderNumber > 1) ?: false,
                        nextSprintExist: sprint.hasNextSprint,
                        displayUrgentTasks: sprint.parentRelease.parentProduct.preferences.displayUrgentTasks,
                        displayRecurrentTasks: sprint.parentRelease.parentProduct.preferences.displayRecurrentTasks,
                        limitValueUrgentTasks: sprint.parentRelease.parentProduct.preferences.limitUrgentTasks,
                        assignOnBeginTask:currentProduct.preferences.assignOnBeginTask,
                        urgentTasksLimited: (urgentTasks.findAll {it.state == Task.STATE_BUSY}.size() >= sprint.parentRelease.parentProduct.preferences.limitUrgentTasks),
                        user: user])
    }


    def updateTable(boolean loadrich, boolean table, String name) {

        withTask{ Task task ->
            if (loadrich) {
                render(status: 200, text: task.notes ?: '')
                return
            }

            if (!table) {
                return
            }

            if (name != 'estimation') {
                if (task.state == Task.STATE_WAIT && name == "state" && params.task.state >= Task.STATE_BUSY) {
                    task.inProgressDate = new Date()
                } else if (task.state == Task.STATE_BUSY && name == "state" && params.task.state == Task.STATE_WAIT) {
                    task.inProgressDate = null
                }
                task.properties = params.task
            }
            else {
                params.task.estimation = params.task?.estimation?.replace(/,/,'.')
                task.estimation = params.task?.estimation?.isNumber() ? params.task.estimation.toFloat() : null
            }

            User user = (User) springSecurityService.currentUser
            taskService.update(task, user)

            def returnValue
            if (name == 'notes') {
                returnValue = wikitext.renderHtml(markup: 'Textile', text: task."${name}")
                returnValue = returnValue ?: ''
            } else if (name == 'estimation') {
                returnValue = task.estimation != null ? task.estimation.toString() : '?'
            } else if (name == 'state') {
                returnValue = g.message(code: BundleUtils.taskStates.get(task.state))
                returnValue = returnValue ?: ''
            } else if (name == 'description') {
                returnValue = task.description?.encodeAsHTML()?.encodeAsNL2BR()
                returnValue = returnValue ?: ''
            } else {
                returnValue = task[name].encodeAsHTML()
                returnValue = returnValue ?: ''
            }

            def version = task.isDirty() ? task.version + 1 : task.version
            render(status: 200, text: [version: version, value: returnValue] as JSON)
        }
    }

    def add(long product, long id) {
        def sprint = Sprint.getInProduct(product,id).list()
        if (!sprint) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.sprint.error.not.exist')]] as JSON)
            return
        }
        def stories = sprint ? Story.findAllByParentSprintAndStateLessThanEquals(sprint, Story.STATE_INPROGRESS, [sort: 'rank']) : []

        def selected = null
        if (params.story?.id && !(params.story.id in ['recurrent', 'urgent']))
            selected = Story.getInProduct(product,params.long('story.id')).list()
        else if (params.story?.id && (params.story.id in ['recurrent', 'urgent']))
            selected = [id: params.story?.id]

        def selectList = []
        if (sprint.parentRelease.parentProduct.preferences.displayRecurrentTasks)
            selectList << [id: 'recurrent', name: ' ** ' + message(code: 'is.task.type.recurrent') + ' ** ']
        if (sprint.parentRelease.parentProduct.preferences.displayUrgentTasks)
            selectList << [id: 'urgent', name: ' ** ' + message(code: 'is.task.type.urgent') + ' ** ']
        selectList = selectList + stories

        render(template: 'window/manage', model: [
                sprint: sprint,
                stories: selectList,
                selected: selected,
                colorsLabels: BundleUtils.taskColorsSelect.values().collect { message(code: it) },
                colorsKeys: BundleUtils.taskColorsSelect.keySet().asList(),
                params: [product: product, id: sprint.id]
        ])
    }

    def edit(long product, String referrerUrl) {
        withTask('subid'){Task task ->
            def selected = (task.type == Task.TYPE_RECURRENT) ? [id: 'recurrent'] : (task.type == Task.TYPE_URGENT) ? [id: 'urgent'] : task.parentStory
            def sprint = task.backlog
            def stories = Story.findAllByParentSprintAndStateLessThanEquals((Sprint) sprint, Story.STATE_INPROGRESS, [sort: 'rank'])

            def selectList = []
            if (sprint.parentRelease.parentProduct.preferences.displayRecurrentTasks)
                selectList << [id: 'recurrent', name: ' ** ' + message(code: 'is.task.type.recurrent') + ' ** ']
            if (sprint.parentRelease.parentProduct.preferences.displayUrgentTasks)
                selectList << [id: 'urgent', name: ' ** ' + message(code: 'is.task.type.urgent') + ' ** ']
            selectList = selectList + stories

            User user = (User) springSecurityService.currentUser
            def next = Task.findNextTask(task, user).list()[0]

            render(template: 'window/manage', model: [
                    task: task,
                    referrerUrl: referrerUrl,
                    stories: selectList,
                    selected: selected,
                    next: next?.id ?: '',
                    sprint: task.backlog,
                    colorsLabels: BundleUtils.taskColorsSelect.values().collect { message(code: it) },
                    colorsKeys: BundleUtils.taskColorsSelect.keySet().asList(),
                    params: [product: product, id: task.id]
            ])
        }
    }

    def editStory(long product, long id) {
        forward(action: 'edit', controller: 'story', params: [referrer: controllerName, id: id, product: product])
    }

    def doneDefinition() {
        withSprint{ Sprint sprint ->
            render(template: 'window/doneDefinition', model: [sprint: sprint])
        }
    }

    @Secured('(productOwner() or scrumMaster()) and !archivedProduct()')
    def copyFromPreviousDoneDefinition(long product) {
        withSprint{ Sprint sprint ->
            if (sprint.orderNumber > 1 || sprint.parentRelease.orderNumber > 1) {
                def previous
                if (sprint.orderNumber > 1) {
                    previous = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber - 1)
                } else {
                    previous = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.parentRelease.sprints.size())
                }
                sprint.doneDefinition = previous.doneDefinition
            } else {
                render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.sprint.error.doneDefinition.no.previous')]] as JSON)
            }
            sprintService.updateDoneDefinition(sprint)
            redirect(action: 'doneDefinition', params: [product: product, id: sprint.id])
        }
    }

    def retrospective() {
        withSprint{ Sprint sprint ->
            render(template: 'window/retrospective', model: [sprint: sprint])
        }
    }

    @Secured('(productOwner() or scrumMaster()) and !archivedProduct()')
    def copyFromPreviousRetrospective() {
        withSprint{ Sprint sprint ->
            if (sprint.orderNumber > 1 || sprint.parentRelease.orderNumber > 1) {
                def previous
                if (sprint.orderNumber > 1) {
                    previous = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber - 1)
                } else {
                    previous = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.parentRelease.sprints.size())
                }
                sprint.retrospective = previous.retrospective
            } else {
                render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.sprint.error.retrospective.no.previous')]] as JSON)
            }
            sprintService.updateRetrospective(sprint)
            redirect(action: 'retrospective', params: [product: product, id: sprint.id])
        }
    }

    @Secured('(productOwner() or scrumMaster()) and !archivedProduct()')
    def updateDoneDefinition(String doneDefinition) {
        withSprint{ Sprint sprint ->
            sprint.doneDefinition = doneDefinition
            sprintService.updateDoneDefinition(sprint)
            render(status: 200)
        }
    }

    @Secured('(productOwner() or scrumMaster()) and !archivedProduct()')
    def updateRetrospective(String retrospective) {
        withSprint{ Sprint sprint ->
            sprint.retrospective = retrospective
            sprintService.updateRetrospective(sprint)
            render(status: 200)
        }
    }

    @Cacheable(cache = "sprintCache", keyGenerator = 'sprintKeyGenerator')
    def sprintBurndownHoursChart() {
        withSprint{ Sprint sprint ->
            def values = sprintService.sprintBurndownHoursValues(sprint)
            if (values.size() > 0) {
                render(template: 'charts/sprintBurndownHoursChart', model: [
                        remainingHours: values.remainingHours as JSON,
                        idealHours: values.idealHours as JSON,
                        withButtonBar: (params.withButtonBar != null) ? params.boolean('withButtonBar') : true,
                        labels: values.label as JSON])
            } else {
                returnError(text:message(code: 'is.chart.error.no.values'))
            }
        }
    }

    @Cacheable(cache = "sprintCache", keyGenerator = 'sprintKeyGenerator')
    def sprintBurnupTasksChart() {
        withSprint{ Sprint sprint ->
            def values = sprintService.sprintBurnupTasksValues(sprint)
            if (values.size() > 0) {
                render(template: 'charts/sprintBurnupTasksChart', model: [
                        tasks: values.tasks as JSON,
                        withButtonBar: (params.withButtonBar != null) ? params.boolean('withButtonBar') : true,
                        tasksDone: values.tasksDone as JSON,
                        labels: values.label as JSON])
            } else {
                returnError(text:message(code: 'is.chart.error.no.values'))
            }
        }
    }

    @Cacheable(cache = "sprintCache", keyGenerator = 'sprintKeyGenerator')
    def sprintBurnupStoriesChart() {
        withSprint{ Sprint sprint ->
            def values = sprintService.sprintBurnupStoriesValues(sprint)
            if (values.size() > 0) {
                render(template: 'charts/sprintBurnupStoriesChart', model: [
                        stories: values.stories as JSON,
                        withButtonBar: (params.withButtonBar != null) ? params.boolean('withButtonBar') : true,
                        storiesDone: values.storiesDone as JSON,
                        labels: values.label as JSON])
            } else {
                returnError(text:message(code: 'is.chart.error.no.values'))
            }
        }
    }

    @Cacheable(cache = "sprintCache", keyGenerator = 'sprintKeyGenerator')
    def sprintBurnupPointsChart() {
        withSprint{ Sprint sprint ->
            def values = sprintService.sprintBurnupStoriesValues(sprint)
            if (values.size() > 0) {
                render(template: 'charts/sprintBurnupPointsChart', model: [
                        points: values.totalPoints as JSON,
                        withButtonBar: (params.withButtonBar != null) ? params.boolean('withButtonBar') : true,
                        pointsDone: values.pointsDone as JSON,
                        labels: values.label as JSON])
            } else {
                returnError(text:message(code: 'is.chart.error.no.values'))
            }
        }
    }

    def changeFilterTasks(long product, long id, String filter) {
        if (!filter || !filter in ['allTasks', 'myTasks', 'freeTasks']) {
            def msg = message(code: 'is.user.preferences.error.not.filter')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
            return
        }
        User user = (User) springSecurityService.currentUser
        user.preferences.filterTask = filter
        userService.update(user)
        redirect(action: 'index', params: [product: product, id: id])
    }

    def changeHideDoneState(long product, long id) {
        User user = (User) springSecurityService.currentUser
        user.preferences.hideDoneState = !user.preferences.hideDoneState
        userService.update(user)
        redirect(action: 'index', params: [product: product, id: id])
    }

    def copyRecurrentTasksFromPreviousSprint() {
        withSprint{ Sprint sprint ->
            def tasks = sprintService.copyRecurrentTasksFromPreviousSprint(sprint)
            render(status: 200, contentType: 'application/json', text: tasks as JSON)
        }
    }

    /**
     * Export the sprint backlog elements in multiple format (PDF, DOCX, RTF, ODT)
     */
    def print(long product, long id, String locationHash, boolean get, boolean status, String format) {
        def currentProduct = Product.load(product)
        def sprint = Sprint.getInProduct(product,id).list()
        def data
        def chart = null

        if (!sprint) {
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.sprint.error.not.exist')]] as JSON)
            return
        }

        if (locationHash) {
            chart = processLocationHash(locationHash.decodeURL()).action
        }

        switch (chart) {
            case 'sprintBurndownHoursChart':
                data = sprintService.sprintBurndownHoursValues(sprint)
                break
            case 'sprintBurnupTasksChart':
                data = sprintService.sprintBurnupTasksValues(sprint)
                break
            case 'sprintBurnupStoriesChart':
                data = sprintService.sprintBurnupStoriesValues(sprint)
                break
            default:
                chart = 'sprintPlan'

                // Retrieve all the tasks associated to the sprint
                def stories = Story.findStoriesFilter(sprint, null, null).listDistinct()
                def tasks = Task.findRecurrentTasksFilter(sprint, null, null).listDistinct() + Task.findUrgentTasksFilter(sprint, null, null).listDistinct()
                stories.each {
                    tasks = tasks + it.tasks
                }
                // Workaround to force gorm to fetch the associated data (required for jasper to access the data)
                tasks.each {
                    it.parentStory?.name
                    it.responsible?.lastName
                    it.creator?.lastName
                }
                data = [
                        [
                                taskStateBundle: BundleUtils.taskStates,
                                tasks: tasks,
                                sprintBurndownHoursChart: sprintService.sprintBurndownHoursValues(sprint),
                                sprintBurnupTasksChart: sprintService.sprintBurnupTasksValues(sprint),
                                sprintBurnupStoriesChart: sprintService.sprintBurnupStoriesValues(sprint)
                        ]
                ]
                break
        }

        if (data.size() <= 0) {
            returnError(text:message(code: 'is.report.error.no.data'))
        } else if (get) {
            outputJasperReport(chart ?: 'sprintPlan', format, data, currentProduct.name, ['labels.projectName': currentProduct.name])
        } else if (status) {
            render(status: 200, contentType: 'application/json', text: session?.progress as JSON)
        } else {
            session.progress = new ProgressSupport()
            def dialog = g.render(template: '/scrumOS/report')
            render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
        }
    }

    def printPostits(long product, String locationHash, boolean get, boolean status, String format) {
        def currentProduct = Product.load(product)
        withSprint{ Sprint sprint ->
            def stories1 = []
            def stories2 = []
            def first = 0
            if (!sprint.stories) {
                returnError(text:message(code: 'is.report.error.no.data'))
                return
            } else if (get) {
                sprint.stories?.each {
                    def story = [name: it.name,
                            id: it.uid,
                            effort: it.effort,
                            state: message(code: BundleUtils.storyStates[it.state]),
                            description: is.storyTemplate([story: it, displayBR: true]),
                            notes: wikitext.renderHtml([markup: 'Textile'], it.notes).decodeHTML(),
                            type: message(code: BundleUtils.storyTypes[it.type]),
                            suggestedDate: it.suggestedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.suggestedDate]) : null,
                            acceptedDate: it.acceptedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.acceptedDate]) : null,
                            estimatedDate: it.estimatedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.estimatedDate]) : null,
                            plannedDate: it.plannedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.plannedDate]) : null,
                            inProgressDate: it.inProgressDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.inProgressDate]) : null,
                            doneDate: it.doneDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: currentProduct.preferences.timezone, date: it.doneDate ?: null]) : null,
                            rank: it.rank ?: null,
                            sprint: g.message(code: 'is.release') + " " + sprint.parentRelease.orderNumber + " - " + g.message(code: 'is.sprint') + " " + sprint.orderNumber,
                            creator: it.creator.firstName + ' ' + it.creator.lastName,
                            feature: it.feature?.name ?: null,
                            dependsOn: it.dependsOn?.name ? it.dependsOn.uid + " " + it.dependsOn.name : null,
                            permalink:createLink(absolute: true, mapping: "shortURL", params: [product: currentProduct.pkey], id: it.uid),
                            featureColor: it.feature?.color ?: null]
                    if (first == 0) {
                        stories1 << story
                        first = 1
                    } else {
                        stories2 << story
                        first = 0
                    }

                }
                outputJasperReport('stories', format, [[product: currentProduct.name, stories1: stories1 ?: null, stories2: stories2 ?: null]], currentProduct.name)
            } else if (status) {
                render(status: 200, contentType: 'application/json', text: session?.progress as JSON)
            } else {
                session.progress = new ProgressSupport()
                def dialog = g.render(template: '/scrumOS/report', model: [sprint: sprint])
                render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
            }
        }
    }

    /**
     * Parse the location hash string passed in argument
     * @param locationHash
     * @return A Map
     */
    private processLocationHash(String locationHash) {
        def data = locationHash.split('/')
        return [
                controller: data[0].replace('#', ''),
                action: data.size() > 1 ? data[1] : null
        ]
    }
}