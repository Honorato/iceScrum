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
- Nicolas Noullet (nnoullet@kagilum.com)
--}%
<table class="table table-striped"
    id="widget-${controllerName}"
    data-binding
    data-binding-type="actor"
    data-binding-selector="tr"
    data-binding-tpl="tpl-actor-row"
    data-binding-watch="items"
    data-binding-highlight="true"
    data-binding-config="actors">
</table>
<entry:point id="${controllerName}-${actionName}-widget" model="[actors:actors]"/>