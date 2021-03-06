/**
 *
 * Copyright (C) 2009 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.http.pool;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Named;

import org.jclouds.Constants;
import org.jclouds.http.HttpCommandRendezvous;
import org.jclouds.lifecycle.BaseLifeCycle;

/**
 * // TODO: Adrian: Document this!
 * 
 * @author Adrian Cole
 */
public abstract class HttpCommandConnectionPool<C> extends BaseLifeCycle {

   protected final Semaphore allConnections;
   protected final BlockingQueue<C> available;

   /**
    * inputOnly: nothing is taken from this queue.
    */
   protected final BlockingQueue<HttpCommandRendezvous<?>> resubmitQueue;
   protected final AtomicInteger currentSessionFailures = new AtomicInteger(0);
   protected volatile boolean hitBottom = false;
   protected final URI endPoint;

   protected int maxConnectionReuse;
   protected int maxSessionFailures;

   public URI getEndPoint() {
      return endPoint;
   }

   public static interface Factory<C> {
      HttpCommandConnectionPool<C> create(URI endPoint);
   }

   public HttpCommandConnectionPool(ExecutorService executor, Semaphore allConnections,
            BlockingQueue<HttpCommandRendezvous<?>> rendezvousQueue, BlockingQueue<C> available,
            URI endPoint, @Named(Constants.PROPERTY_MAX_CONNECTION_REUSE) int maxConnectionReuse,
            @Named(Constants.PROPERTY_MAX_SESSION_FAILURES) int maxSessionFailures) {
      super(executor);
      checkArgument(maxConnectionReuse >= 1, "maxConnectionReuse must be positive");
      checkArgument(maxSessionFailures >= 1, "maxSessionFailures must be positive");
      this.maxConnectionReuse = maxConnectionReuse;
      this.maxSessionFailures = maxSessionFailures;
      this.allConnections = allConnections;
      this.resubmitQueue = rendezvousQueue;
      this.available = available;
      this.endPoint = endPoint;
   }

   protected void setResponseException(Exception ex, C conn) {
      HttpCommandRendezvous<?> rendezvous = getHandleFromConnection(conn).getCommandRendezvous();
      setExceptionOnCommand(ex, rendezvous);
   }

   protected void cancel(C conn) {
      HttpCommandRendezvous<?> rendezvous = getHandleFromConnection(conn).getCommandRendezvous();
      rendezvous.cancel();
   }

   protected C getConnection() throws InterruptedException, TimeoutException {
      exceptionIfNotActive();
      if (!hitBottom) {
         hitBottom = available.size() == 0 && allConnections.availablePermits() == 0;
         if (hitBottom)
            logger.warn("saturated connection pool");
      }
      logger.trace("Blocking up to %ds for a connection to %s", 5, getEndPoint());
      C conn = available.poll(5, TimeUnit.SECONDS);
      if (conn == null)
         throw new TimeoutException(String.format("Timeout after %ds for a connection to %s", 5,
                  getEndPoint()));

      if (connectionValid(conn)) {
         return conn;
      } else {
         logger.debug("Connection %s unusable for endpoint %s", conn.hashCode(), getEndPoint());
         shutdownConnection(conn);
         allConnections.release();
         return getConnection();
      }
   }

   protected void fatalException(Exception ex, C conn) {
      setResponseException(ex, conn);
      exception.set(ex);
      shutdown();
   }

   protected abstract void shutdownConnection(C conn);

   protected abstract boolean connectionValid(C conn);

   public HttpCommandConnectionHandle<C> getHandle(HttpCommandRendezvous<?> rendezvous)
            throws InterruptedException, TimeoutException {
      exceptionIfNotActive();
      C conn = getConnection();
      HttpCommandConnectionHandle<C> handle = createHandle(rendezvous, conn);
      associateHandleWithConnection(handle, conn);
      return handle;
   }

   protected abstract HttpCommandConnectionHandle<C> createHandle(
            HttpCommandRendezvous<?> rendezvous, C conn);

   protected void resubmitIfRequestIsReplayable(C connection, Exception e) {
      HttpCommandRendezvous<?> rendezvous = getCommandFromConnection(connection);
      if (rendezvous != null) {
         if (isReplayable(rendezvous)) {
            logger.info("resubmitting request: %s", rendezvous.getCommand().getRequest()
                     .getRequestLine());
            resubmitQueue.add(rendezvous);
         } else {
            setExceptionOnCommand(e, rendezvous);
         }
      }
   }

   protected abstract boolean isReplayable(HttpCommandRendezvous<?> rendezvous);

   protected HttpCommandRendezvous<?> getCommandFromConnection(C connection) {
      HttpCommandConnectionHandle<C> handle = getHandleFromConnection(connection);
      if (handle != null && handle.getCommandRendezvous() != null) {
         return handle.getCommandRendezvous();
      }
      return null;
   }

   protected void setExceptionOnCommand(C connection, Exception e) {
      HttpCommandRendezvous<?> rendezvous = getCommandFromConnection(connection);
      if (rendezvous != null) {
         setExceptionOnCommand(e, rendezvous);
      }
   }

   protected void setExceptionOnCommand(Exception e, HttpCommandRendezvous<?> rendezvous) {
      logger.warn(e, "Exception processing command: %s", rendezvous.getCommand());
      try {
         rendezvous.setException(e);
      } catch (InterruptedException e1) {
         logger.error(e, "interrupted setting exception on command", rendezvous.getCommand());
      }
   }

   protected abstract void associateHandleWithConnection(HttpCommandConnectionHandle<C> handle,
            C connection);

   protected abstract HttpCommandConnectionHandle<C> getHandleFromConnection(C connection);

   protected abstract void createNewConnection() throws InterruptedException;

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((endPoint == null) ? 0 : endPoint.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      HttpCommandConnectionPool<?> other = (HttpCommandConnectionPool<?>) obj;
      if (endPoint == null) {
         if (other.endPoint != null)
            return false;
      } else if (!endPoint.equals(other.endPoint))
         return false;
      return true;
   }

}
