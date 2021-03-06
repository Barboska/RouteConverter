/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/
package slash.navigation.catalog.domain;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class CategorysIT extends RouteCatalogServiceBase {

    @Test
    public void testGetRoot() throws IOException {
        Category root = routeCatalog.getRootCategory();
        assertNotNull(root);
        assertEquals("", root.getName());
        assertNull(root.getDescription());
    }

    private Category addSubCategory(Category root, String name) throws IOException {
        int before = root.getSubCategories().size();
        Category category = root.addSubCategory(name);
        int after = root.getSubCategories().size();
        assertEquals(before + 1, after);
        assertEquals(name, category.getName());
        assertEquals(name, root.getSubCategories().get(after - 1).getName());
        Category find = root.getSubCategory(name);
        assertNotNull(find);
        assertEquals(name, find.getName());
        return find;
    }

    @Test
    public void testAddSubCategoryWithSpaces() throws Exception {
        addSubCategory(routeCatalog.getRootCategory(), "Spaces Category " + System.currentTimeMillis());
    }

    @Test
    public void testAddSubCategoryWithUmlauts() throws Exception {
        addSubCategory(routeCatalog.getRootCategory(), "Umlauts äöüßÄÖÜ Category " + System.currentTimeMillis());
    }

    @Test
    public void testAddSubCategoryWithUmlautsBelowCategoryWithUmlauts() throws Exception {
        Category category = addSubCategory(routeCatalog.getRootCategory(), "Umlauts äöüßÄÖÜ Category " + System.currentTimeMillis());
        addSubCategory(category, "Umlauts äöüßÄÖÜ Category " + System.currentTimeMillis());
    }

    @Test
    public void testAddSubCategoryWithSlashes() throws Exception {
        try {
            addSubCategory(routeCatalog.getRootCategory(), "/Slashes/Category/" + System.currentTimeMillis() + "/");
            assertTrue(false);
        } catch (IOException e) {
        }
    }

    @Test
    public void testAddSubCategoryWithPluses() throws Exception {
        try {
            addSubCategory(routeCatalog.getRootCategory(), "A + B + C" + System.currentTimeMillis() + "/");
            assertTrue(false);
        } catch (IOException e) {
        }
    }

    @Test
    public void testRename() throws Exception {
        String name = "Category " + System.currentTimeMillis();
        Category root = routeCatalog.getRootCategory();
        Category category = root.addSubCategory(name);
        String rename = "Renamed " + name;
        category.updateCategory(null, rename);
        assertEquals(rename, category.getName());
        Category find = root.getSubCategory(rename);
        assertNotNull(find);
        assertEquals(rename, find.getName());
    }

    @Test
    public void testRenameWithNullParentParameter() throws Exception {
        String name = "Category " + System.currentTimeMillis();
        Category root = routeCatalog.getRootCategory();
        Category category = root.addSubCategory(name);
        String rename = "Renamed " + name;
        category.updateCategory(null, rename);
        assertEquals(rename, category.getName());
        Category find = root.getSubCategory(rename);
        assertNotNull(find);
        assertEquals(rename, find.getName());
    }

    @Test
    public void testRenameCategoryWithSlashes() throws Exception {
        String name = "Category " + System.currentTimeMillis();
        Category root = routeCatalog.getRootCategory();
        Category category = root.addSubCategory(name);
        String rename = "Slashes / Category / " + name;
        try {
            category.updateCategory(root, rename);
            assertTrue(false);
        } catch (IOException e) {
        }
    }

    @Test
    public void testMove() throws Exception {
        Category root = routeCatalog.getRootCategory();
        String firstName = "First Category " + System.currentTimeMillis();
        Category first = root.addSubCategory(firstName);
        String secondName = "Second Category " + System.currentTimeMillis();
        Category second = root.addSubCategory(secondName);

        String name = "Category " + System.currentTimeMillis();
        Category category = first.addSubCategory(name);

        String rename = "Moved " + name;
        category.updateCategory(second, rename);
        assertEquals(rename, category.getName());
        Category find = first.getSubCategory(rename);
        assertNull(find);
        find = second.getSubCategory(rename);
        assertNotNull(find);
        assertEquals(rename, find.getName());
    }

    @Test
    public void testMoveToSelfAsParent() throws Exception {
        Category root = routeCatalog.getRootCategory();
        String parentName = "Parent " + System.currentTimeMillis();
        Category parent = root.addSubCategory(parentName);
        String moveName = "Move " + System.currentTimeMillis();
        Category move = parent.addSubCategory(moveName);

        try {
            move.updateCategory(move, move.getName());
            assertTrue(false);
        } catch (IOException e) {
        }
    }

    @Test
    public void testMoveToOwnChild() throws Exception {
        Category root = routeCatalog.getRootCategory();
        String parentName = "Parent " + System.currentTimeMillis();
        Category parent = root.addSubCategory(parentName);
        String moveName = "Move " + System.currentTimeMillis();
        Category move = parent.addSubCategory(moveName);
        String childName = "Child " + System.currentTimeMillis();
        Category child = move.addSubCategory(childName);

        try {
            move.updateCategory(child, move.getName());
            assertTrue(false);
        } catch (IOException e) {
        }
    }
}
