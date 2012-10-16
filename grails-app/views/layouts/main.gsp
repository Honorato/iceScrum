%{--
- Copyright (c) 2010 iceScrum Technologies.
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
- Damien vitrac (damien@oocube.com)
- Manuarii Stein (manuarii.stein@icescrum.com)
- Stephane Maldini (stephane.maldini@icescrum.com)
--}%
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<head>
    <title>iceScrum -
        <g:layoutTitle/></title>
    <r:external uri="/${is.currentThemeImage()}favicon.ico"/>
<!--[if IE 8]><meta http-equiv="X-UA-Compatible" content="IE=8"/><![endif]-->
    <is:loadJsVar/>
    <r:require modules="jquery,jquery-ui,jquery-ui-plugins,tagit,jquery-plugins,jqplot,icescrum${grailsApplication.config?.modulesResources ? ','+grailsApplication.config.modulesResources.join(',') : ''}"/>
    <sec:ifLoggedIn>
        <script type="text/javascript" src="${grailsApplication.config.grails.serverURL}/js/timeline/timeline_ajax/simile-ajax-api.js?bundle=true"></script>
        <script type="text/javascript" src="${grailsApplication.config.grails.serverURL}/js/timeline/timeline_js/timeline-api.js?bundle=true"></script>
        <script type="text/javascript" src="${grailsApplication.config.grails.serverURL}/js/timeline/icescrum-painter.js"></script>
    </sec:ifLoggedIn>
    <r:layoutResources/>
    <g:layoutHead/>
</head>

<body class="icescrum">

<div id="application">
    <div id="head" class="${product ? 'is_header-normal' : 'is_header-full'}">
        <is:mainMenu/>
    </div>

    <div id="local">
        <div class="widget-bar">
          <div id="widget-list">
            <sec:ifLoggedIn>
                <div class="upgrade" style="display:none;">
                    <span class="close"><g:message code="is.ui.hide"/></span>
                    <g:message code="is.ui.hide"/>
                </div>
            </sec:ifLoggedIn>
          </div>
        </div>
        <div id="notifications" style="display:none;"><a id="accept_notifications">${message(code:'is.ui.html5.notifications')}</a> (<a id="hide_notifications">${message(code:'is.ui.hide')}</a>)</div>
    </div>
    <is:desktop>
        <g:layoutBody/>
    </is:desktop>
</div>
<is:spinner
        on401="var data = jQuery.parseJSON(xhr.responseText); document.location='${createLink(controller:'login',action:'auth')}?ref=${product ? 'p/'+product.pkey+'/' : ''}'+(data.url?data.url:'');"
        on400="var error = jQuery.parseJSON(xhr.responseText); jQuery.icescrum.renderNotice( error.notice.text, 'error', error.notice.title); "
        on403="jQuery.icescrum.renderNotice('${message(code:'is.error.denied')}', 'error');"
        on500="jQuery.icescrum.dialogError(xhr)"/>
<r:layoutResources/>
<jq:jquery>
    $("#main-content").droppable({
      drop:function(event, ui){
        var id = ui.draggable.attr('id').replace('widget-id-','');
        if (id == ui.draggable.attr('id')){
          id = ui.draggable.attr('id').replace('elem_','');
        }
        $.icescrum.openWindow(id);
      },
      hoverClass: 'main-active',
      accept: '.draggable-to-desktop'
    });
      $("#widget-list").sortable({
        handle:".box-title",
        items:".box-widget-sortable"
      });

      $("#local").droppable({
        drop:function(event, ui){
          var id = ui.draggable.attr('id').replace('elem_','');
          if (id != ui.draggable.attr('id')){
            if($("#window-id-"+id).is(':visible')){
              $.icescrum.windowToWidget($("#window-id-"+id),event);
            }else{
              $.icescrum.addToWidgetBar(id);
            }
          }
        },
        hoverClass: 'local-active',
        accept: '.widgetable'
      });
</jq:jquery>
<entry:point id="icescrum-footer"/>
<g:include controller="scrumOS" action="templates" params="[product:params.product]"/>
<is:onStream
            on="#application"
            events="[[object:'product',events:['add','remove','update','redirect','archive', 'unarchive']]]"/>
<is:onStream
            on="#application"
            events="[[object:'user',events:['addRoleProduct','removeRoleProduct','updateRoleProduct','updateProfile']]]"/>
</body>
</html>