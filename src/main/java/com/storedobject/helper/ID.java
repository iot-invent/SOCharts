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

import com.vaadin.flow.component.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A unique id generator that can be used to set a unique id for components.
 *
 * @author Syam
 */
public class ID {

    private static final AtomicLong ID = new AtomicLong(1L);

    private ID() {
    }

    /**
     * Get a new id.
     *
     * @return New unique id.
     */
    public static long newID() {
        if(ID.get() == Long.MAX_VALUE) {
            ID.set(1);
        }
        return ID.getAndIncrement();
    }

    /**
     * Set a unique id for the given component if it doesn't have an id now.
     * (Id set will be of the format "soN" where N will be a unique long number).
     *
     * @param component Component for which id needs to be set.
     */
    public static void set(Component component) {
        if(component.getId().isEmpty()) {
            component.setId("so" + newID());
        }
    }
}
