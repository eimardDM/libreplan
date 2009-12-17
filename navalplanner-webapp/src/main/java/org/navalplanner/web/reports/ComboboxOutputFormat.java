/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.reports;

import org.zkoss.zk.ui.HtmlMacroComponent;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;

public class ComboboxOutputFormat extends HtmlMacroComponent {

    private static final long serialVersionUID = 1L;

    public String getOutputFormat() {
        return getSelectedItem().getLabel().toLowerCase();
    }

    private Comboitem getSelectedItem() {
        final Comboitem item = getCombobox().getSelectedItem();
        return (item != null) ? item : getDefaultItem();
    }

    private Combobox getCombobox() {
        return (Combobox) this.getFellowIfAny("combobox");
    }

    private Comboitem getDefaultItem() {
        return getCombobox().getItemAtIndex(0);
    }

}
