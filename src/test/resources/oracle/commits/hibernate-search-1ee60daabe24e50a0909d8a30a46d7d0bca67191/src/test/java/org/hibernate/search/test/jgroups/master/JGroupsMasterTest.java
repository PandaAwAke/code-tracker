// $Id$
package org.hibernate.search.test.jgroups.master;

import java.io.Serializable;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.jgroups.JChannel;
import org.jgroups.Message;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.Environment;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.backend.AddLuceneWork;
import org.hibernate.search.backend.LuceneWork;
import org.hibernate.search.backend.impl.jgroups.JGroupsBackendQueueProcessorFactory;
import org.hibernate.search.engine.DocumentBuilder;
import org.hibernate.search.test.SearchTestCase;
import org.hibernate.search.test.jms.master.TShirt;

/**
 * Tests that the Master node in a JGroups cluster can properly process messages received from channel.
 * <p/>
 * In case of running test outside Hibernate Search Maven configuration set following VM configuration:
 * <br><br>
 * <code>
 * -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1
 * </code>
 *
 * @author Lukasz Moren
 */
public class JGroupsMasterTest extends SearchTestCase {

	/**
	 * Name of the JGroups channel used in test
	 */
	public static final String CHANNEL_NAME = "jgroups_test_channel";

	private JChannel channel;

	public void testMessageSending() throws Exception {

		TShirt shirt = createObjectWithSQL();
		List<LuceneWork> queue = createDocumentAndWorkQueue( shirt );

		sendMessage( queue );

		Thread.sleep( 3000 );

		FullTextSession ftSess = Search.getFullTextSession( openSession() );
		ftSess.getTransaction().begin();
		QueryParser parser = new QueryParser( "id", new StopAnalyzer() );
		Query luceneQuery = parser.parse( "logo:jboss" );
		org.hibernate.Query query = ftSess.createFullTextQuery( luceneQuery );
		List result = query.list();
		assertEquals( 1, result.size() );
		ftSess.delete( result.get( 0 ) );
		ftSess.getTransaction().commit();
		ftSess.close();
	}

	private void prepareJGroupsChannel() throws Exception {
		channel = new JChannel( prepareJGroupsCongigurationString() );
		channel.connect( CHANNEL_NAME );
	}

	private void sendMessage(List<LuceneWork> queue) throws Exception {
		//send message to all listeners
		Message message = new Message( null, null, ( Serializable ) queue );
		channel.send( message );
	}

	/**
	 * Manually create the work queue. This lists gets send by the Slaves to the Master for indexing.
	 *
	 * @param shirt The shirt to index
	 *
	 * @return A manually create <code>LuceneWork</code> list.
	 */
	private List<LuceneWork> createDocumentAndWorkQueue(TShirt shirt) {
		Document doc = new Document();
		Field field = new Field(
				DocumentBuilder.CLASS_FIELDNAME, shirt.getClass().getName(), Field.Store.YES, Field.Index.NOT_ANALYZED
		);
		doc.add( field );
		field = new Field( "id", "1", Field.Store.YES, Field.Index.NOT_ANALYZED );
		doc.add( field );
		field = new Field( "logo", shirt.getLogo(), Field.Store.NO, Field.Index.ANALYZED );
		doc.add( field );
		LuceneWork luceneWork = new AddLuceneWork(
				shirt.getId(), String.valueOf( shirt.getId() ), shirt.getClass(), doc
		);
		List<LuceneWork> queue = new ArrayList<LuceneWork>();
		queue.add( luceneWork );
		return queue;
	}

	/**
	 * Create a test object without trigggering indexing. Use SQL directly.
	 *
	 * @return a <code>TShirt</code> test object.
	 *
	 * @throws java.sql.SQLException in case the inset fails.
	 */
	@SuppressWarnings({ "deprecation" })
	private TShirt createObjectWithSQL() throws SQLException {
		Session s = openSession();
		s.getTransaction().begin();
		Statement statement = s.connection().createStatement();
		statement.executeUpdate(
				"insert into TShirt_Master(id, logo, size) values( '1', 'JBoss balls', 'large')"
		);
		statement.close();
		TShirt ts = ( TShirt ) s.get( TShirt.class, 1 );
		s.getTransaction().commit();
		s.close();
		return ts;
	}

	public static Session getSession() throws HibernateException {
		return sessions.openSession();
	}

	protected void setUp() throws Exception {
		prepareJGroupsChannel();
		super.setUp();
	}

	protected void tearDown() throws Exception {
		channel.close();
		super.tearDown();
	}

	protected void configure(Configuration cfg) {
		super.configure( cfg );
		// JGroups configuration for master node
		cfg.setProperty( Environment.WORKER_BACKEND, "jgroupsMaster" );
		cfg.setProperty( JGroupsBackendQueueProcessorFactory.JG_CLUSTER_NAME, CHANNEL_NAME );
		cfg.setProperty(
				JGroupsBackendQueueProcessorFactory.CONFIGURATION_STRING, prepareJGroupsCongigurationString()
		);
	}

	private String prepareJGroupsCongigurationString() {
		return "UDP(mcast_addr=228.1.2.3;mcast_port=45566;ip_ttl=32):" +
				"PING(timeout=3000;num_initial_members=6):" +
				"FD(timeout=5000):" +
				"VERIFY_SUSPECT(timeout=1500):" +
				"pbcast.NAKACK(gc_lag=10;retransmit_timeout=3000):" +
				"UNICAST(timeout=5000):" +
				"FRAG:" +
				"pbcast.GMS(join_timeout=3000;" +
				"shun=false;print_local_addr=true)";
	}

	protected Class[] getMappings() {
		return new Class[] {
				TShirt.class
		};
	}
}