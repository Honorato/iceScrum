/*
 * Copyright (c) 2014 Kagilum.
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
 */

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException
import org.springframework.transaction.CannotCreateTransactionException
import com.mysql.jdbc.CommunicationsException

class UrlMappings {
    static mappings = {

        name default: "/$controller/$action/$id?" {
        }

        "/$action" {
            controller = 'scrumOS'
        }

        "/$action/$window?/$id?" {
            controller = 'scrumOS'
            constraints {
                id(matches: /\d*/)
            }
        }

        "/$action/$window?/$actionWindow?/$id?" {
            controller = 'scrumOS'
            constraints {
                actionWindow(matches: /[a-zA-Z]*/)
                id(matches: /\d*/)
            }
        }

        "/" {
            controller = 'scrumOS'
            action = 'index'
        }

        "/textileParser" {
            controller = 'scrumOS'
            action = 'textileParser'
        }

        name privateURL: "/ws/$controller/$action/$id?" {
        }

        "/login"(controller: 'login', action: 'auth')

        "/user" {
            controller = 'user'
            action = [GET: "list", POST:"save"]
        }

        "/user/$id" {
            controller = 'user'
            action = [GET: "index", PUT:"update", POST:"update"]
            constraints {
                id(matches: /\d*/)
            }
        }

        "403"(controller: "errors", action: "error403")
        "400"(controller: "errors", action: "fakeError")
        "302"(controller: "errors", action: "fakeError")
        "500"(controller: "errors", action: "error403", exception: AccessDeniedException)
        "500"(controller: "errors", action: "error403", exception: NotFoundException)
        "500"(controller: 'errors', action: 'memory', exception: OutOfMemoryError)
        "500"(controller: 'errors', action: 'database', exception: CannotCreateTransactionException)
        "500"(controller: 'errors', action: 'database', exception: CommunicationsException)
    }
}