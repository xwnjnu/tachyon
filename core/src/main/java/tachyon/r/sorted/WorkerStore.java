package tachyon.r.sorted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;

import com.google.common.collect.ImmutableList;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.client.TachyonFS;
import tachyon.extension.ComponentException;
import tachyon.extension.WorkerComponent;
import tachyon.thrift.SortedStorePartitionInfo;

public class WorkerStore extends WorkerComponent {
  private final TachyonURI URI;

  private final Logger LOG = Logger.getLogger(Constants.LOGGER_TYPE);

  // TODO Using TachyonFS is a trick for now.
  private TachyonFS mTFS;

  // Mapping: <storeId, <partitionIndex, Partition>>
  private HashMap<Integer, HashMap<Integer, WorkerPartition>> mData;

  public WorkerStore(TachyonURI uri) throws IOException {
    URI = uri;
    LOG.info(URI.toString());
    mTFS = TachyonFS.get(URI.toString());
    mData = new HashMap<Integer, HashMap<Integer, WorkerPartition>>();
  }

  public byte[] get(SortedStorePartitionInfo info, byte[] key) throws IOException {
    if (!mData.containsKey(info.getStoreId())) {
      mData.put(info.getStoreId(), new HashMap<Integer, WorkerPartition>());
    }

    HashMap<Integer, WorkerPartition> store = mData.get(info.getStoreId());
    if (!store.containsKey(info.getPartitionIndex())) {
      store.put(info.getPartitionIndex(), new WorkerPartition(mTFS, info));
    }

    return store.get(info.getPartitionIndex()).get(key);
  }

  @Override
  public List<ByteBuffer> process(List<ByteBuffer> data) throws ComponentException {
    if (data.size() < 1) {
      throw new ComponentException("Data List is empty");
    }

    WorkerOperationType opType = null;
    try {
      opType = WorkerOperationType.getOpType(data.get(0));
    } catch (IOException e) {
      throw new ComponentException(e);
    }

    try {
      switch (opType) {
      case GET: {
        checkLength(data, 3);

        TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());
        SortedStorePartitionInfo info = new SortedStorePartitionInfo();
        deserializer.deserialize(info, data.get(1).array());

        return ImmutableList.of(ByteBuffer.wrap(get(info, data.get(2).array())));
      }
      }
    } catch (TException e) {
      throw new ComponentException(e);
    } catch (IOException e) {
      throw new ComponentException(e);
    }

    throw new ComponentException("Unprocessed MasterOperationType " + opType);
  }

  private void checkLength(List<ByteBuffer> data, int length) throws ComponentException {
    if (data.size() != length) {
      throw new ComponentException("Corrupted data, wrong data length " + data.size()
          + " . Right length is " + length);
    }
  }
}
