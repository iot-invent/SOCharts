/*
 *  Copyright 2019-2020 Syam Pillai
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.storedobject.helper;

import com.vaadin.flow.component.HasSize;

/**
 * A component that extends the functionality of {@link LitComponent} and implements the {@link HasSize} interface
 * to support setting size-related properties such as width, height, minimum size, and maximum size.
 * <p>This class provides methods to set the size, as well as individual dimensions like width, height,
 * minimum width, minimum height, maximum width, and maximum height for the associated web component.
 * It leverages the Vaadin front-end element mechanisms to set the size properties dynamically.</p>
 * <p>Assumption: The component has size-related properties such as width, height, minw, minh, maxw, maxh.</p>
 *
 * @author Syam
 */
public class LitComponentWithSize extends LitComponent implements HasSize {

    /**
     * Set the size.
     *
     * @param width Width.
     * @param height Height.
     */
    public void setSize(String width, String height) {
        setWidth(width);
        setHeight(height);
    }

    @Override
    public void setWidth(String width) {
        HasSize.super.setWidth(width);
        getElement().setProperty("width", width);
    }

    @Override
    public void setHeight(String height) {
        HasSize.super.setHeight(height);
        getElement().setProperty("height", height);
    }

    @Override
    public void setMinWidth(String minWidth) {
        HasSize.super.setMinWidth(minWidth);
        getElement().setProperty("minw", minWidth);
    }

    @Override
    public void setMinHeight(String minHeight) {
        HasSize.super.setMinHeight(minHeight);
        getElement().setProperty("minh", minHeight);
    }

    @Override
    public void setMaxWidth(String maxWidth) {
        HasSize.super.setMaxWidth(maxWidth);
        getElement().setProperty("maxw", maxWidth);
    }

    @Override
    public void setMaxHeight(String maxHeight) {
        HasSize.super.setMaxHeight(maxHeight);
        getElement().setProperty("maxh", maxHeight);
    }
}
