%{--
- Copyright (c) 2012 Kagilum.
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
<a href="#${controllerName}" data-container="body" data-toggle="tooltip" class="btn btn-danger" title="${message(code:'is.ui.actor.toolbar.new')}">
    <i class="glyphicon glyphicon-plus"></i>
</a>
<entry:point id="${controllerName}-${actionName}-widget-toolbar"/>