/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.persistence;

import org.drools.persistence.map.ManualTransactionManager;
import org.jbpm.persistence.processinstance.ProcessInstanceInfo;

public class ManualProcessTransactionManager extends ManualTransactionManager {

    private ProcessStorage storage;
    private NonTransactionalProcessPersistentSession session;

    public ManualProcessTransactionManager(NonTransactionalProcessPersistentSession session,
                                           ProcessStorage storage) {
        super( session,
               storage );
        this.storage = storage;
        this.session = session;
    }
    
    @Override
    public void commit(boolean transactionOwner) {
        for ( ProcessInstanceInfo processInstanceInfo : session.getStoredProcessInstances() ) {
            storage.saveOrUpdate( processInstanceInfo );
        }
        session.clearStoredProcessInstances();
        super.commit(transactionOwner);
    }
}
