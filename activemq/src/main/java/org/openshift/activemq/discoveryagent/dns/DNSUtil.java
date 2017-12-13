/**
 *  Copyright 2015 Red Hat, Inc.
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
package org.openshift.activemq.discoveryagent.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for querying OpenShift DNS server for services and endpoints.
 */
public class DNSUtil {

    private final static Logger LOGGER = LoggerFactory.getLogger(DNSUtil.class);

    /**
     * Create a new DNSUtil using default DNS server (i.e. dns:)
     */
    public DNSUtil() {
    }

    /**
     * Returns a list of IP addresses for the given name.
     * 
     * @param name the name to lookup
     * @return the list of IPs for the name
     */
    public String[] lookupIPs(String name) {
        if (name == null) {
            return new String[0];
        }
        try {
            List<String> retVal = new ArrayList<String>();
            for (InetAddress inetAddress : InetAddress.getAllByName(name)) {
                retVal.add(inetAddress.getHostAddress());
            }
            return retVal.toArray(new String[retVal.size()]);
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not resolve host: {}", name, e);
            return new String[0];
        }
    }

    /**
     * Return the port for the specified service.
     * 
     * @param name the service name
     * @return the port
     */
    public String getPortForService(String name) {
        if (name == null) {
            return null;
        }
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put(Context.PROVIDER_URL, "dns:");
            env.put("com.sun.jndi.dns.recursion", "false");
            // default is one second, but os skydns can be slow
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "4");
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_tcp." + name, new String[] {"SRV" });
            if (attrs == null) {
                return null;
            }
            for (NamingEnumeration<?> srvs = attrs.getAll(); srvs.hasMore();) {
                String srv = srvs.next().toString();
                String[] fields = srv.split(" ");
                return fields[2];
            }
        } catch (NamingException e) {
            LOGGER.error("Error retrieving port for service: " + name, e.getMessage());
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    e.fillInStackTrace();
                }
            }
        }
        return null;
    }

}
