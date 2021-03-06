%{--
- Copyright (c) 2011 Kagilum SAS.
-
- This file is part of iceScrum.
-
- iceScrum is free software: you can redistribute it and/or modify
- it under the terms of the GNU Affero General Public License as published by
- the Free Software Foundation, either version 3 of the License.
-
- iceScrum is distributed in the hope that it will be useful,
- but WITHOUT ANY WARRANTY; without even the implied warranty of
- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
- GNU General Public License for more details.
-
- You should have received a copy of the GNU Affero General Public License
- along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
-
- Authors:
-
- Nicolas Noullet (nnoullet@kagilum.com)
--}%
<%@ page import="org.icescrum.core.domain.Story; org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState" %>
<g:set var="testEditable" value="${template || (request.inProduct && parentStory.state < Story.STATE_DONE)}"/>
<g:set var="stateEditable" value="${template || (request.inProduct && parentStory.state == Story.STATE_INPROGRESS)}"/>
<li id="acceptance-test${acceptanceTest.id}" class="acceptance-test ${last?'last':''}" data-elemid="${acceptanceTest.id}">
    <div class="acceptance-test-content">

        <div class="acceptance-test-state">
            <g:if test="${stateEditable}">
                <is:select
                        width="105"
                        class="acceptance-test-state-select"
                        id="acceptance-test-state-select${acceptanceTest?.id ?: ''}"
                        name="acceptanceTest.state"
                        data-icon-class="select-icon-acceptance-test icon-acceptance-test"
                        from="${AcceptanceTestState.values().collect{ message(code: it.toString()) }}"
                        keys="${AcceptanceTestState.values().id}"
                        value="${acceptanceTest.state}"
                        data-url="${createLink(controller: 'acceptanceTest', action: 'update', id: acceptanceTest.id, params: [product: params.product])}"/>
            </g:if>
            <g:else>
                <div class="text-icon-acceptance-test icon-acceptance-test${acceptanceTest?.state ?: ''}">
                    ${message(code: acceptanceTest.stateEnum.toString())}
                </div>
            </g:else>
        </div>

        <div class="acceptance-test-name">
            ${acceptanceTest.uid} - <strong>${acceptanceTest.name}</strong>
            <g:if test="${testEditable}">
                <span class="acceptance-test-menu">
                    (
                        <a href="${createLink(controller:"acceptanceTest",action: "editor",params:[product:params.product,id:acceptanceTest.id])}"
                           data-ajax="true"
                           data-ajax-update="#acceptance-test-editor-wrapper${acceptanceTest.id}"
                           data-ajax-success="jQuery('#acceptance-test-form-container').hide();
                                             jQuery('#acceptance-test${acceptanceTest.id} .acceptance-test-content').hide();
                                             jQuery('#acceptance-test-editor-wrapper${acceptanceTest?.id ?: ''}').show();">
                            ${message(code:'is.ui.acceptanceTest.edit')}
                        </a>
                    -
                        <is:link history="false"
                                remote="true"
                                controller="acceptanceTest"
                                action="delete"
                                id="${acceptanceTest.id}"
                                onSuccess="jQuery.event.trigger('remove_acceptancetest',data);">
                            ${message(code:'is.ui.acceptanceTest.delete')}
                        </is:link>
                    )
                </span>
            </g:if>
        </div>

        <div class="acceptance-test-description">
            <g:if test="${template}">
                ${acceptanceTest.description}
            </g:if>
            <g:else>
                <div class="rich-content"><wikitext:renderHtml markup="Textile">${acceptanceTest.description}</wikitext:renderHtml></div>
            </g:else>
        </div>
    </div>

    <div id="acceptance-test-editor-wrapper${acceptanceTest.id}"></div>

</li>
