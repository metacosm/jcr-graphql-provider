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
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.util.Locale;

/**
 * @author Christophe Laprun
 */
abstract class ItemsDataFetcher<T> extends JCRDataFetcher<T> {
    protected String ws;
    protected Locale lang;

    ItemsDataFetcher(JCRQraphQLQueryProvider queryProvider) {
        super(queryProvider);
    }

    @Override
    protected T perform(DataFetchingEnvironment environment, JCRSessionWrapper session) throws RepositoryException {
        GQLItems parent = (GQLItems) environment.getSource();
        final GQLNode itemsParent = parent.getParent();
        ws = itemsParent.getWs();
        lang = itemsParent.getLang();

        final JCRNodeWrapper node = session.getNodeByIdentifier(itemsParent.getId());

        String name = environment.getArgument("name");
        if (name == null) {
            name = JCRQraphQLQueryProvider.unescape(environment.getFields().get(0).getName());
        }

        return perform(environment, session, node, name);
    }

    protected abstract T perform(DataFetchingEnvironment environment, JCRSessionWrapper session, JCRNodeWrapper node,
                                 String childName) throws RepositoryException;

    @Override
    protected String getWs() {
        return ws;
    }

    @Override
    protected Locale getLang() {
        return lang;
    }
}