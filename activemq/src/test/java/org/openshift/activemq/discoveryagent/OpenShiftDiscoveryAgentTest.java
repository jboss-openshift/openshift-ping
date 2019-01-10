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
package org.openshift.activemq.discoveryagent;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.command.DiscoveryEvent;
import org.apache.activemq.transport.discovery.DiscoveryListener;
import org.apache.activemq.util.IntrospectionSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class OpenShiftDiscoveryAgentTest {

   private final static Logger LOGGER = LoggerFactory.getLogger(OpenShiftDiscoveryAgentTest.class);

   @Test
   public void testSameAddressStopStart() throws Exception {

      final AtomicInteger addCount = new AtomicInteger();
      final CountDownLatch first = new CountDownLatch(1);
      final CountDownLatch second = new CountDownLatch(1);


      PeerAddressResolver peerAddressResolver = new PeerAddressResolver() {
         @Override
         public String getServiceName() {
            return "bb";
         }

         @Override
         public String[] getPeerIPs() {
            try {
               TimeUnit.SECONDS.sleep(0);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
            return new String[]{"10.10.10.10"};
         }

         @Override
         public int getServicePort() {
            return 0;
         }
      };

      OpenShiftDiscoveryAgent underTest = new OpenShiftDiscoveryAgent(peerAddressResolver);

      underTest.setDiscoveryListener(new DiscoveryListener() {
         @Override
         public void onServiceAdd(DiscoveryEvent discoveryEvent) {
            LOGGER.info("Add: " + discoveryEvent);
            switch (addCount.incrementAndGet()) {
               case 1: {
                  first.countDown();
                  break;
               }
               case 2: {
                  second.countDown();
                  break;
               }
               default:
            }
         }

         @Override
         public void onServiceRemove(DiscoveryEvent discoveryEvent) {
            LOGGER.info("Remove: " + discoveryEvent);

         }
      });

      underTest.setQueryInterval(1);
      underTest.start();

      assertTrue("first add", first.await(10, TimeUnit.SECONDS));
      underTest.stop();

      underTest.start();
      assertTrue("second add", second.await(10, TimeUnit.SECONDS));

      assertEquals("add count", 2, addCount.get());

   }

   @Test
   public void testReconnectRetryConfigExposed() throws Exception {

      final AtomicInteger addCount = new AtomicInteger();
      final AtomicInteger removeCount = new AtomicInteger();


      PeerAddressResolver peerAddressResolver = new PeerAddressResolver() {
         @Override
         public String getServiceName() {
            return "bb";
         }

         @Override
         public String[] getPeerIPs() {
            try {
               TimeUnit.SECONDS.sleep(0);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
            return new String[]{"10.10.10.10"};
         }

         @Override
         public int getServicePort() {
            return 99;
         }
      };

      final OpenShiftDiscoveryAgent underTest = new OpenShiftDiscoveryAgent(peerAddressResolver);


      underTest.setDiscoveryListener(new DiscoveryListener() {
         @Override
         public void onServiceAdd(DiscoveryEvent discoveryEvent) {
            LOGGER.info("Add: " + discoveryEvent);
            addCount.getAndIncrement();
            try {
               // fail immediately - service not available!
               underTest.serviceFailed(discoveryEvent);
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

         @Override
         public void onServiceRemove(DiscoveryEvent discoveryEvent) {
            LOGGER.info("Remove: " + discoveryEvent);
            removeCount.getAndIncrement();
         }
      });

      final HashMap<String, String> options = new HashMap<>();
      options.put("maxReconnectAttempts", "2");
      options.put("minConnectTime", "50");
      options.put("initialReconnectDelay", "100");
      options.put("queryInterval", "1");
      IntrospectionSupport.setProperties(underTest, options);

      assertTrue("all props applied: " + options, options.isEmpty());

      underTest.start();

      TimeUnit.SECONDS.sleep(2);
      assertEquals("add count", 2, addCount.get());
      assertEquals("remove count", 2, addCount.get());

   }

}