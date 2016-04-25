package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.ICallChainTreeDao;
import com.ai.cloud.skywalking.web.dto.AnlyResult;
import com.ai.cloud.skywalking.web.dto.CallChainTree;
import com.ai.cloud.skywalking.web.dto.CallChainTreeNode;
import com.ai.cloud.skywalking.web.entity.BreviaryChainTree;
import com.ai.cloud.skywalking.web.util.HBaseUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Calendar;
import java.util.Map;

@Repository
public class CallChainTreeDao implements ICallChainTreeDao {

    private Logger logger = LogManager.getLogger(BreviaryChainTree.class);

    @Autowired
    private HBaseUtils hBaseUtils;

    @Override
    public AnlyResult queryEntranceAnlyResult(String entranceColumnName, String treeId) throws IOException {
        String columnName = null;
        if (entranceColumnName.lastIndexOf(":") != -1) {
            columnName = entranceColumnName.substring(0, entranceColumnName.length() - 1);
        }
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf("sw-chain-1month-summary"));
        Get get = new Get(treeId.getBytes());
        Result result = table.get(get);
        if (result.rawCells().length == 0) {
            Calendar calendar = Calendar.getInstance();
            return new AnlyResult(calendar.get(Calendar.YEAR) + "", (calendar.get(Calendar.MONTH) + 1) + "");
        }
        AnlyResult anlyResult = null;
        Cell cell = result.getColumnLatestCell("chain_summary".getBytes(), columnName.getBytes());
        if (cell != null) {
            String anlyResultStr = Bytes.toString(cell.getValueArray(),
                    cell.getValueOffset(), cell.getValueLength());
            logger.debug("traceId: {} , entranceColumnName : {}, anlyResultStr : {}",
                    treeId, columnName, anlyResultStr);
            JsonObject jsonObject = (JsonObject) new JsonParser().parse(anlyResultStr);
            Map<String, AnlyResult> resultMap = new Gson().fromJson(jsonObject.getAsJsonObject("summaryValueMap"),
                    new TypeToken<Map<String, AnlyResult>>() {
                    }.getType());
            anlyResult = resultMap.get((Calendar.getInstance().get(Calendar.MONTH) + 1) + "");
        }

        if (anlyResult == null) {
            anlyResult = new AnlyResult();
        }
        anlyResult.setYearOfAnlyResult((Calendar.getInstance().get(Calendar.YEAR)) + "");
        anlyResult.setMonthOfAnlyResult((Calendar.getInstance().get(Calendar.MONTH) + 1) + "");
        return anlyResult;
    }

    @Override
    public CallChainTree queryAnalysisCallTree(String tableName, String rowKey, String loadKey) throws IOException {
        Table table = hBaseUtils.getConnection().getTable(TableName.valueOf(tableName));
        Get get = new Get(rowKey.getBytes());
        Result result = table.get(get);
        if (result.rawCells().length == 0) {
            return null;
        }
        CallChainTree chainTree = new CallChainTree();
        for (Cell cell : result.rawCells()) {
            String qualifierStr = Bytes.toString(cell.getQualifierArray(),
                    cell.getQualifierOffset(), cell.getQualifierLength());
            String valueStr = Bytes.toString(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
            CallChainTreeNode callChainTreeNode = new CallChainTreeNode(qualifierStr, valueStr, loadKey);
            chainTree.addNode(callChainTreeNode);
        }

        return chainTree;
    }
}