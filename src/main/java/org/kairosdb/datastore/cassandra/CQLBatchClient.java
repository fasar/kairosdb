package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import org.kairosdb.core.DataPoint;
import org.kairosdb.util.KDataOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.kairosdb.datastore.cassandra.CassandraDatastore.*;

/**
 Created by bhawkins on 12/12/16.
 */
public class CQLBatchClient implements BatchClient
{
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final Session m_session;
	private final PreparedStatement m_psInsertData;
	private final PreparedStatement m_psInsertRowKey;
	private final PreparedStatement m_psInsertString;

	private BatchStatement metricNamesBatch = new BatchStatement(BatchStatement.Type.UNLOGGED);
	private BatchStatement tagNameBatch = new BatchStatement(BatchStatement.Type.UNLOGGED);
	private BatchStatement tagValueBatch = new BatchStatement(BatchStatement.Type.UNLOGGED);
	private BatchStatement dataPointBatch = new BatchStatement(BatchStatement.Type.UNLOGGED);
	private BatchStatement rowKeyBatch = new BatchStatement(BatchStatement.Type.UNLOGGED);

	public CQLBatchClient(Session session, PreparedStatement psInsertData,
			PreparedStatement psInsertRowKey, PreparedStatement psInsertString)
	{
		m_session = session;
		m_psInsertData = psInsertData;
		m_psInsertRowKey = psInsertRowKey;
		m_psInsertString = psInsertString;
	}

	@Override
	public void addRowKey(String metricName, DataPointsRowKey rowKey, int rowKeyTtl)
	{
		BoundStatement bs = new BoundStatement(m_psInsertRowKey);
		bs.setBytes(0, ByteBuffer.wrap(metricName.getBytes(UTF_8)));
		bs.setBytes(1, DATA_POINTS_ROW_KEY_SERIALIZER.toByteBuffer(rowKey));
		bs.setInt(2, rowKeyTtl);
		//m_session.executeAsync(bs);
		rowKeyBatch.add(bs);
	}

	@Override
	public void addMetricName(String metricName)
	{
		BoundStatement bs = new BoundStatement(m_psInsertString);
		bs.setBytes(0, ByteBuffer.wrap(ROW_KEY_METRIC_NAMES.getBytes(UTF_8)));
		bs.setString(1, metricName);

		//m_session.executeAsync(bs);
		metricNamesBatch.add(bs);
	}

	@Override
	public void addTagName(String tagName)
	{
		BoundStatement bs = new BoundStatement(m_psInsertString);
		bs.setBytes(0, ByteBuffer.wrap(ROW_KEY_TAG_NAMES.getBytes(UTF_8)));
		bs.setString(1, tagName);

		tagNameBatch.add(bs);
	}

	@Override
	public void addTagValue(String value)
	{
		BoundStatement bs = new BoundStatement(m_psInsertString);
		bs.setBytes(0, ByteBuffer.wrap(ROW_KEY_TAG_VALUES.getBytes(UTF_8)));
		bs.setString(1, value);

		tagValueBatch.add(bs);
	}

	@Override
	public void addDataPoint(DataPointsRowKey rowKey, int columnTime, DataPoint dataPoint, int ttl) throws IOException
	{
		KDataOutput kDataOutput = new KDataOutput();
		dataPoint.writeValueToBuffer(kDataOutput);

		BoundStatement boundStatement = new BoundStatement(m_psInsertData);
		boundStatement.setBytes(0, DATA_POINTS_ROW_KEY_SERIALIZER.toByteBuffer(rowKey));
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(columnTime);
		b.rewind();
		boundStatement.setBytes(1, b);
		boundStatement.setBytes(2, ByteBuffer.wrap(kDataOutput.getBytes()));
		boundStatement.setInt(3, ttl);

		dataPointBatch.add(boundStatement);
	}

	@Override
	public void submitBatch()
	{
		if (metricNamesBatch.size() != 0)
			m_session.executeAsync(metricNamesBatch);

		if (tagNameBatch.size() != 0)
			m_session.executeAsync(tagNameBatch);

		if (tagValueBatch.size() != 0)
			m_session.executeAsync(tagValueBatch);

		if (rowKeyBatch.size() != 0)
			m_session.executeAsync(rowKeyBatch);

		m_session.execute(dataPointBatch);
	}
}
