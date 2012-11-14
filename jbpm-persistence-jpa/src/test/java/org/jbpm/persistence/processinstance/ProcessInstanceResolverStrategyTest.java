package org.jbpm.persistence.processinstance;

import static org.kie.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;
import static org.jbpm.persistence.util.PersistenceUtil.*;

import java.util.HashMap;
import java.util.Map;

import javax.naming.InitialContext;
import javax.persistence.*;
import javax.transaction.UserTransaction;

import org.kie.KnowledgeBase;

import org.drools.io.impl.ClassPathResource;
import org.kie.builder.KnowledgeBuilder;
import org.kie.builder.KnowledgeBuilderFactory;
import org.kie.builder.ResourceType;
import org.kie.marshalling.ObjectMarshallingStrategy;
import org.drools.marshalling.impl.ClassObjectMarshallingStrategyAcceptor;
import org.drools.marshalling.impl.SerializablePlaceholderResolverStrategy;
import org.kie.persistence.jpa.JPAKnowledgeService;
import org.drools.persistence.jpa.marshaller.JPAPlaceholderResolverStrategy;

import org.kie.runtime.Environment;
import org.kie.runtime.EnvironmentName;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkflowProcessInstance;
import org.jbpm.marshalling.impl.ProcessInstanceResolverStrategy;
import org.jbpm.persistence.processinstance.objects.NonSerializableClass;
import org.jbpm.persistence.util.PersistenceUtil;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceResolverStrategyTest {

    private static Logger logger = LoggerFactory.getLogger(ProcessInstanceResolverStrategyTest.class);
    
    private HashMap<String, Object> context;
    private StatefulKnowledgeSession ksession;

    private static final String RF_FILE = "SimpleProcess.rf";
    private final static String PROCESS_ID = "org.jbpm.persistence.TestProcess";
    private final static String VAR_NAME = "persistVar";
   
    @Before
    public void before() { 
        context = setupWithPoolingDataSource(JBPM_PERSISTENCE_UNIT_NAME, false);
        
        // load up the knowledge base
        Environment env = PersistenceUtil.createEnvironment(context);
        env.set(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, new ObjectMarshallingStrategy[] {
                new ProcessInstanceResolverStrategy(),
                new JPAPlaceholderResolverStrategy(env),
                new SerializablePlaceholderResolverStrategy(ClassObjectMarshallingStrategyAcceptor.DEFAULT) }
                );
        KnowledgeBase kbase = loadKnowledgeBase();

        // create session
        ksession = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null, env);
        Assert.assertTrue("Valid KnowledgeSession could not be created.", ksession != null && ksession.getId() > 0);
    }
    
    private KnowledgeBase loadKnowledgeBase() { 
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( new ClassPathResource( RF_FILE ), ResourceType.DRF );
        KnowledgeBase kbase = kbuilder.newKnowledgeBase();
        return kbase;
    }
    
    
    @After
    public void after() {
        if( ksession != null ) { 
            ksession.dispose();
        }
        cleanUp(context);
    }

    @Test
    public void testWithDatabaseAndStartProcess() throws Exception {
        // Create variable
        Map<String, Object> params = new HashMap<String, Object>();
        NonSerializableClass processVar = new NonSerializableClass();
        processVar.setString("1234567890");
        params.put(VAR_NAME, processVar);
        params.put("logger", logger);

        // Persist variable
        UserTransaction ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        EntityManagerFactory emf = (EntityManagerFactory) context.get(ENTITY_MANAGER_FACTORY);
        EntityManager em = emf.createEntityManager();
        em.setFlushMode(FlushModeType.COMMIT);
        em.joinTransaction();
        em.persist(processVar);
        em.close();
        ut.commit();

        // Generate, insert, and start process
        ProcessInstance processInstance = ksession.startProcess(PROCESS_ID, params);

        // Test resuls
        Assert.assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        processVar = (NonSerializableClass) ((WorkflowProcessInstance) processInstance).getVariable(VAR_NAME);
        Assert.assertNotNull(processVar);
    }

    @Test
    public void testWithDatabaseAndStartProcessInstance() throws Exception {
        // Create variable
        Map<String, Object> params = new HashMap<String, Object>();
        NonSerializableClass processVar = new NonSerializableClass();
        processVar.setString("1234567890");
        params.put(VAR_NAME, processVar);
    
        // Persist variable
        UserTransaction ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        EntityManagerFactory emf = (EntityManagerFactory) context.get(ENTITY_MANAGER_FACTORY);
        EntityManager em = emf.createEntityManager();
        em.setFlushMode(FlushModeType.COMMIT);
        em.joinTransaction();
        em.persist(processVar);
        em.close();
        ut.commit();
    
        // Create process,
        ProcessInstance processInstance = ksession.createProcessInstance(PROCESS_ID, params);
        long processInstanceId = processInstance.getId();
        Assert.assertTrue(processInstanceId > 0);
        Assert.assertEquals(ProcessInstance.STATE_PENDING, processInstance.getState());
        
        // insert process,
        ksession.insert(processInstance);
   
        // and start process
        ksession.startProcessInstance(processInstanceId);
        ksession.fireAllRules();
    
        // Test results
        processInstance = ksession.getProcessInstance(processInstanceId);
        Assert.assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        processVar = (NonSerializableClass) ((WorkflowProcessInstance) processInstance).getVariable(VAR_NAME);
        Assert.assertNotNull(processVar);
    }

}
