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

package org.openshift.ping.common;

import static org.openshift.ping.common.Utils.getSystemEnvInt;
import static org.openshift.ping.common.Utils.trimToNull;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.PING;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.openshift.ping.common.compatibility.CompatibilityException;
import org.openshift.ping.common.compatibility.CompatibilityUtils;
import org.openshift.ping.common.server.ServerFactory;

public abstract class OpenshiftPing extends PING {

    private String clusterName;

    private final String _systemEnvPrefix;

    @Property
    private int connectTimeout = 5000;
    private int _connectTimeout;

    @Property
    private int readTimeout = 30000;
    private int _readTimeout;

    @Property
    private int operationAttempts = 3;
    private int _operationAttempts;

    @Property
    private long operationSleep = 1000;
    private long _operationSleep;

    private static Method sendDownMethod; //handled via reflection due to JGroups 3/4 incompatibility

    public OpenshiftPing(String systemEnvPrefix) {
        super();
        _systemEnvPrefix = trimToNull(systemEnvPrefix);
        try {
            if(CompatibilityUtils.isJGroups4()) {
                sendDownMethod = Protocol.class.getMethod("down", Message.class);
            } else {
                sendDownMethod = Protocol.class.getMethod("down", Event.class);
            }
        } catch (Exception e) {
            throw new CompatibilityException("Could not find suitable 'up' method.", e);
        }
    }

    protected final String getSystemEnvName(String systemEnvSuffix) {
        StringBuilder sb = new StringBuilder();
        String suffix = trimToNull(systemEnvSuffix);
        if (suffix != null) {
            if (_systemEnvPrefix != null) {
                sb.append(_systemEnvPrefix);
            }
            sb.append(suffix);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    protected final int getConnectTimeout() {
        return _connectTimeout;
    }

    protected final int getReadTimeout() {
        return _readTimeout;
    }

    protected final int getOperationAttempts() {
        return _operationAttempts;
    }

    protected final long getOperationSleep() {
        return _operationSleep;
    }

    protected abstract boolean isClusteringEnabled();

    protected abstract int getServerPort();

    public final void setServerFactory(ServerFactory serverFactory) {
    }

    @Override
    public void init() throws Exception {
        super.init();
        _connectTimeout = getSystemEnvInt(getSystemEnvName("CONNECT_TIMEOUT"), connectTimeout);
        _readTimeout = getSystemEnvInt(getSystemEnvName("READ_TIMEOUT"), readTimeout);
        _operationAttempts = getSystemEnvInt(getSystemEnvName("OPERATION_ATTEMPTS"), operationAttempts);
        _operationSleep = (long) getSystemEnvInt(getSystemEnvName("OPERATION_SLEEP"), (int) operationSleep);
    }

    @Override
    public void destroy() {
        _connectTimeout = 0;
        _readTimeout = 0;
        _operationAttempts = 0;
        _operationSleep = 0l;
        super.destroy();
    }

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public Object down(Event evt) {
        switch (evt.getType()) {
        case Event.CONNECT:
        case Event.CONNECT_WITH_STATE_TRANSFER:
        case Event.CONNECT_USE_FLUSH:
        case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH:
            clusterName = (String) evt.getArg();
            break;
        }
        return super.down(evt);
    }

    public void handlePingRequest(InputStream stream) throws Exception {
        throw new UnsupportedOperationException("handlePingRequest() is no longer supported.");
    }

    private void sendDown(Object obj, Message msg) {
        try {
            if(CompatibilityUtils.isJGroups4()) {
                sendDownMethod.invoke(obj, msg);
            } else {
                sendDownMethod.invoke(obj, new Event(1, msg));
            }
        } catch (Exception e) {
            throw new CompatibilityException("Could not invoke 'down' method.", e);
        }
    }

    private List<InetSocketAddress> readAll() {
        if (isClusteringEnabled()) {
            return doReadAll(clusterName);
        } else {
            return Collections.emptyList();
        }
    }

    protected abstract List<InetSocketAddress> doReadAll(String clusterName);

    @Override
    protected void sendMcastDiscoveryRequest(Message msg) {
        final List<InetSocketAddress> hosts = readAll();
        final PhysicalAddress physical_addr = (PhysicalAddress) down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
        if (!(physical_addr instanceof IpAddress)) {
            log.error("Unable to send PING requests: physical_addr is not an IpAddress.");
            return;
        }
        // XXX: is it better to force this to be defined?
        // assume symmetry
        final int port = ((IpAddress) physical_addr).getPort();
        for (InetSocketAddress host: hosts) {
            // JGroups messages cannot be reused - https://github.com/belaban/workshop/blob/master/slides/admin.adoc#problem-9-reusing-a-message-the-sebastian-problem
            Message msgToHost = msg.copy();
            msgToHost.dest(new IpAddress(host.getAddress(), port));
            sendDown(down_prot, msgToHost);
        }
    }

}
