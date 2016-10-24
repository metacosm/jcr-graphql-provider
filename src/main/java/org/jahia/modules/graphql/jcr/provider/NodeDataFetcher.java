/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 * http://www.jahia.com
 *
 * Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
 *
 * THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 * 1/GPL OR 2/JSEL
 *
 * 1/ GPL
 * ==================================================================================
 *
 * IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * 2/ JSEL - Commercial and Supported Versions of the program
 * ===================================================================================
 *
 * IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 * Alternatively, commercial and supported versions of the program - also known as
 * Enterprise Distributions - must be used in accordance with the terms and conditions
 * contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.graphql.jcr.provider;

import graphql.schema.DataFetchingEnvironment;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Locale;

/**
 * @author Christophe Laprun
 */
class NodeDataFetcher extends JCRDataFetcher<GQLNode> {
    private String ws;
    private Locale lang;

    NodeDataFetcher(GraphQLNodeRegistry registry) {
        super(registry);
    }

    @Override
    protected boolean isEnvironmentValid(DataFetchingEnvironment environment) {
        final String id = environment.getArgument("id");
        final String path = environment.getArgument("path");

        if (id == null && path == null) {
            throw new IllegalArgumentException("Should provide at least a node path or identifier");
        }

        final String wsArg = environment.getArgument("ws");
        this.ws = wsArg == null ? Constants.EDIT_WORKSPACE : ws;
        String langArg = environment.getArgument("lang");
        lang = langArg == null ? Locale.ENGLISH : Locale.forLanguageTag(langArg);

        return true;
    }

    @Override
    protected GQLNode perform(DataFetchingEnvironment environment, JCRSessionWrapper session) throws RepositoryException {
        Node node;
        final String id = environment.getArgument("id");
        if (id != null) {
            node = session.getNodeByIdentifier(id);
        } else {
            final String path = environment.getArgument("path");
            node = session.getNode(path);
        }

        return new GQLNode(node, ws, lang);
    }

    @Override
    protected String getWs() {
        return ws;
    }

    @Override
    protected Locale getLang() {
        return lang;
    }
}
