/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.openshift.ping.kube.test;

import static org.openshift.ping.common.Utils.readFileToString;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.openshift.ping.kube.Client;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class TestClient extends Client {

    private final Map<String, String> OPS = new HashMap<>();

    public TestClient() {
        this("/pods.json");
    }

    public TestClient(String resourceFile) {
        super(null, null, 0, 0, 0, 0, null);
        String json;
        try {
            json = readFileToString(new File(TestClient.class.getResource(resourceFile).toURI()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        OPS.put("pods", json);
    }

    @Override
    protected ModelNode getNode(String op, String namespace, String labels) throws Exception {
        String value = OPS.get(op);
        if (value == null) {
            throw new IllegalStateException("No such op: " + op);
        }
        return ModelNode.fromJSONString(value);
    }
}
