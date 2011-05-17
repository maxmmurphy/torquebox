/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.torquebox.messaging;

import java.util.List;
import java.util.Set;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

import org.hibernate.validator.metadata.BeanMetaData;
import org.jboss.as.ee.naming.ContextNames;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceController.Mode;
import org.torquebox.core.as.CoreServices;
import org.torquebox.core.component.ComponentResolver;
import org.torquebox.core.runtime.RubyRuntimePool;
import org.torquebox.core.util.StringUtils;
import org.torquebox.messaging.as.MessagingServices;

/**
 * <pre>
 * Stage: REAL
 *    In: MessageProcessorMetaData, EnvironmentMetaData
 *   Out: RubyMessageProcessor
 * </pre>
 * 
 */
public class MessageProcessorDeployer implements DeploymentUnitProcessor {

    public MessageProcessorDeployer() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        List<MessageProcessorMetaData> allMetaData = unit.getAttachmentList( MessageProcessorMetaData.ATTACHMENT_KEY );

        for (MessageProcessorMetaData each : allMetaData) {
            deploy( phaseContext, each );
        }
    }
    
    protected void deploy(DeploymentPhaseContext phaseContext, MessageProcessorMetaData metaData) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        
        final String name = metaData.getName();
        
        ClassLoader classLoader = unit.getAttachment( Attachments.MODULE ).getClassLoader();
        
        int concurrency = metaData.getConcurrency();

        for ( int i = 0 ; i < concurrency ; ++i ) {
            ServiceName baseServiceName = MessagingServices.messageProcessor( unit, name );
            MessageProcessorService service = new MessageProcessorService(classLoader);
            service.setName( metaData.getName() );
            service.setMessageSelector( metaData.getMessageSelector() );
            service.setDurable( metaData.getDurable() );
            
            phaseContext.getServiceTarget().addService( baseServiceName.append( "" + i ), service )
                .addDependency( MessagingServices.messageProcessorComponentResolver( unit, name ), ComponentResolver.class, service.getComponentResolverInjector() )
                .addDependency( getConnectionFactoryServiceName(), ManagedReferenceFactory.class, service.getConnectionFactoryInjector() )
                .addDependency( getDestinationServiceName( metaData.getDestinationName() ), ManagedReferenceFactory.class, service.getDestinationInjector() )
                .addDependency( CoreServices.runtimePoolName( unit, "messaging" ), RubyRuntimePool.class, service.getRuntimePoolInjector() )
                .setInitialMode( Mode.ACTIVE )
                .install();
        }
    }
    
    protected ServiceName getConnectionFactoryServiceName() {
        return ContextNames.JAVA_CONTEXT_SERVICE_NAME.append( "ConnectionFactory" );
    }
    
    protected ServiceName getDestinationServiceName(String destination) {
        return ContextNames.JAVA_CONTEXT_SERVICE_NAME.append( destination );
    }


    @Override
    public void undeploy(DeploymentUnit context) {
        
    }
    
}