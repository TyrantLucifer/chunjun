package com.dtstack.flinkx.cdc.store;

import com.dtstack.flinkx.cdc.QueuesChamberlain;

import org.apache.flink.table.data.RowData;

import java.io.Serializable;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tiezhu@dtstack.com
 * @since 2021/12/2 星期四
 */
public abstract class Fetcher implements Runnable, Serializable {

    private QueuesChamberlain chamberlain;

    protected final AtomicBoolean closed = new AtomicBoolean(false);

    public void setChamberlain(QueuesChamberlain chamberlain) {
        this.chamberlain = chamberlain;
    }

    @Override
    public void run() {
        while (!closed.get()) {
            // 遍历block数据队列里的数据
            for (String table : chamberlain.getTableIdentifierFromBlockQueues()) {
                // 取队列中的头节点，查询外部数据源
                Deque<RowData> rowDataDeque = chamberlain.getQueueFromUnblockQueues(table);
                RowData rowData = rowDataDeque.peekFirst();
                // 如果外部数据源已经处理了该数据，那么将此数据从数据队列中移除，此数据队列从block中移除，放入到unblock队列中
                if (fetch(rowData)) {
                    rowDataDeque.removeFirst();
                    chamberlain.dealDmlRowData(table, rowDataDeque);
                }
            }
        }
    }

    /**
     * 查询外部数据源，判断当前data是否被处理
     *
     * @param data 需要查询的外部数据源
     * @return 是否被外部数据源处理
     */
    public abstract boolean fetch(RowData data);

    /**
     * open sub-class
     *
     * @throws Exception exception
     */
    public abstract void open() throws Exception;

    /** 关闭子类 */
    public abstract void closeSubclass();

    public void close() {
        closeSubclass();
        closed.compareAndSet(false, true);
    }
}
