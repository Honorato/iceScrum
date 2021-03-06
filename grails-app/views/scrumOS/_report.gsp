%{--
- Copyright (c) 2014 Kagilum SAS.
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
- Vincent Barrier (vbarrier@kagilum.com)
--}%
<is:modal name="report"
          size="sm"
          title="${message(code:'is.dialog.report.generation')}">
    <p>
        <g:message code="is.dialog.report.description"/>
    </p>
    <div class="progress progress-striped active">
        <div class="progress-bar"
             role="progressbar"
             aria-valuenow="0"
             aria-valuemin="0"
             aria-valuemax="100"
             style="width:0"
             data-ui-progressbar
             data-ui-progressbar-stop-progress-on=".ui-dialog:hidden"
             data-ui-progressbar-get-progress="${createLink(action:actionName,controller:controllerName,params:[product:params.product,status:true], id:params.id?:null)}"
             data-ui-progressbar-download="${createLink(action:actionName,controller:controllerName,params:[product:params.product,get:true,format:params.format], id:params.id?:null)}">
            ${message(code:'is.report.processing')}
        </div>
    </div>
</is:modal>