/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.util;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public enum BundleState {
    ACTIVE              (32),
    INSTALLED           (2),
    RESOLVED            (4),
    STARTING            (8),
    STOPPING            (16),
    UNINSTALLED         (1);

    protected int value;

    private BundleState(int value) {
        this.value = value;
    }

    /**
     * @return the value
     */
    public int getValue() {
        return value;
    }

    public static BundleState getState(int value) {
        switch (value) {
            case 32:
                return ACTIVE;
            case 2:
                return INSTALLED;
            case 4:
                return RESOLVED;
            case 8:
                return STARTING;
            case 16:
                return STOPPING;
            case 1:
                return UNINSTALLED;
        }
        return null;
    }
}
