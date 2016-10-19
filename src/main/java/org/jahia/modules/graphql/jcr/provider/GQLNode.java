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

import org.jahia.api.Constants;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Locale;

/**
 * @author Christophe Laprun
 */
public class GQLNode implements GQLItem {
    private final String name;
    private final String type;
    private final String path;
    private final String id;
    private final GQLItems items;
    private final String ws;
    private final Locale lang;

    static final GQLNode ROOT = new GQLNode("", "rep:root", "/", "cafebabe-cafe-babe-cafe-babecafebabe");

    GQLNode(Node node, String ws, Locale lang) {
        try {
            name = node.getName();
            type = node.getPrimaryNodeType().getName();
            path = node.getPath();
            id = node.getIdentifier();
            items = new GQLItems(this);
            this.ws = ws;
            this.lang = lang;
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private GQLNode(String name, String type, String path, String id) {
        this.name = name;
        this.type = type;
        this.path = path;
        this.id = id;
        this.ws = Constants.EDIT_WORKSPACE;
        this.lang = Locale.ENGLISH;
        items = new GQLItems(this);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getId() {
        return id;
    }

    public GQLItems getChildren() {
        return items;
    }

    public GQLItems getProperties() {
        return items;
    }

    public String getWs() {
        return ws;
    }

    public Locale getLang() {
        return lang;
    }

    @Override
    public String toString() {
        return type + " node id:" + id + " path:" + path + " name: " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GQLNode)) return false;

        GQLNode gqlNode = (GQLNode) o;

        return getId().equals(gqlNode.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
