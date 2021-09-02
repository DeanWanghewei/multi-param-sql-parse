package org.wei.sql.parser.params.parse;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.util.JdbcConstants;
import org.wei.sql.parser.params.entity.ParamsEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.alibaba.druid.sql.ast.expr.SQLBinaryOperator.BooleanOr;

public class MysqlParse {

    public void parseNotMust() {
        String sql = "select * from table where a = ${p1} and b >= ${p2}";
        ArrayList<ParamsEntity> paramsList = new ArrayList<>();

        ParamsEntity p1 = new ParamsEntity("topAgentId", true, "String");
        ParamsEntity p2 = new ParamsEntity("merchantAdminId", true, "String");
        ParamsEntity p3 = new ParamsEntity("storeId", false, "String");
        ParamsEntity p4 = new ParamsEntity("cityCodeList", false, "String");
        ParamsEntity p5 = new ParamsEntity("custId", false, "String");
        ParamsEntity p6 = new ParamsEntity("industryId", false, "String");
        ParamsEntity p7 = new ParamsEntity("productCodeList", false, "String");
        ParamsEntity p8 = new ParamsEntity("isActive", false, "String");
        ParamsEntity p9 = new ParamsEntity("validationFormalTime", false, "String");
        ParamsEntity p10 = new ParamsEntity("firstTransactionTime", false, "String");

        paramsList.add(p1);
        paramsList.add(p2);
        paramsList.add(p3);
        paramsList.add(p4);
        paramsList.add(p5);
        paramsList.add(p6);
        paramsList.add(p7);
        paramsList.add(p8);
        paramsList.add(p9);
        paramsList.add(p10);

        extracted(sql, paramsList);
    }

    private void extracted(String sql, ArrayList<ParamsEntity> paramsList) {
        List<String> paramNameList = paramsList.stream().map(ParamsEntity::getParamsName).map(x -> String.format("${%s}", x)).collect(Collectors.toList());
        List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
        System.out.println(sqlStatements.size());
        for (SQLStatement sqlStatement : sqlStatements) {
            SQLSelectStatement selectStatement = (SQLSelectStatement) sqlStatement;
            SQLSelect select = selectStatement.getSelect();
            parserSelect(select, paramNameList);
        }
        System.out.println(sqlStatements.toString());
    }

    public void parserSelect(SQLSelect select, List<String> paramNameList) {
        SQLSelectQuery query = select.getQuery();
        MySqlSelectQueryBlock selectQueryBlock = (MySqlSelectQueryBlock) query;
        SQLTableSource subFrom = selectQueryBlock.getFrom();
        if (subFrom instanceof SQLSubqueryTableSource) {
            SQLSubqueryTableSource subQueryTable = (SQLSubqueryTableSource) subFrom;
            SQLSelect subQueryTableSelect = subQueryTable.getSelect();
            parserSelect(subQueryTableSelect, paramNameList);
        }
        SQLExpr where = selectQueryBlock.getWhere();
        if (where != null) {
            List<SQLObject> whereChildren = where.getChildren();
            for (SQLObject whereChild : whereChildren) {
                if (haveParams(whereChild, paramNameList)) {
                    if (whereChild instanceof SQLBinaryOpExpr) {
                        SQLBinaryOpExpr binaryExpr = (SQLBinaryOpExpr) whereChild;
                        if (isAllOrNotMustParams(binaryExpr, paramNameList)) {
                            // 处理 某一段condition中都是 or 并且都是非必选参数问题
                            changeItemParamsValue(binaryExpr);
                            binaryExpr.setLeft(new SQLIntegerExpr(1));
                        } else {
                            replaceSQLBinaryOpExpr(binaryExpr, paramNameList);
                        }
                    }
                    if (whereChild instanceof SQLInListExpr) {
                        SQLInListExpr sqlInListExpr = (SQLInListExpr) whereChild;
                        itemSQInListExprParam(sqlInListExpr, paramNameList);
                    }
                }
            }
        }


    }

    public boolean haveParams(Object object, List<String> paramNameList) {
        String s = object.toString();
        long count = paramNameList.stream().filter(s::contains).count();
        return count > 0;
    }


    /**
     * 待解决问题
     * sql中有col in (${test})解析时，无法正常解析。
     * 在一个or语句中 如果or语句中的变量全部非必填。并且全部不传。会导致这个or语句直接为false（可考虑 先检测 如果该括号内都是or并且都没有传。直接删除该语句）
     *
     * @paramsql
     * @paramopExpr
     * @paramnotMustParams
     */
    void replaceSQLBinaryOpExpr(SQLBinaryOpExpr sql, List<String> notMustParams) {
        if (!haveParams(sql, notMustParams)) {
            return;
        }
        SQLExpr left = sql.getLeft();
        SQLExpr right = sql.getRight();
        SQLBinaryOperator operator = sql.getOperator();


        if (left instanceof SQLIdentifierExpr) {
            itemSQLIdentifierExprParam(sql, notMustParams, right, operator);
            return;
        }

        if (left instanceof SQLInListExpr) {
            itemSQInListExprParam((SQLInListExpr) left, notMustParams);
        }

        if (right instanceof SQLInListExpr) {
            itemSQInListExprParam((SQLInListExpr) right, notMustParams);
        }

        if (left instanceof SQLBinaryOpExpr) {
            replaceSQLBinaryOpExpr((SQLBinaryOpExpr) left, notMustParams);
        }

        if (right instanceof SQLBinaryOpExpr) {
            replaceSQLBinaryOpExpr((SQLBinaryOpExpr) right, notMustParams);
        }
    }


    private void itemSQInListExprParam(SQLInListExpr sql, List<String> notMustParams) {
        if (!haveParams(sql, notMustParams)) {
            return;
        }
        String value = buildSQLInListIdentifier(sql.getTargetList());
        if (haveParamsCondition(notMustParams, value, null)) {
            sql.setExpr(new SQLIntegerExpr(1));
            SQLBinaryOperator parentOperator = ((SQLBinaryOpExpr) sql.getParent()).getOperator();
            switch (parentOperator) {
                case BooleanOr:
                    sql.setTargetList(Collections.singletonList(new SQLIntegerExpr(2)));
                    break;
                default:
                    sql.setTargetList(Collections.singletonList(new SQLIntegerExpr(1)));
            }
        }
    }

    private void itemSQLIdentifierExprParam(SQLBinaryOpExpr sql, List<String> notMustParams, SQLExpr right, SQLBinaryOperator operator) {
        String value = buildSQLIdentifier(right);
        if (haveParamsCondition(notMustParams, value, operator)) {
            changeItemParamsValue(sql);
        }
    }

    private void changeItemParamsValue(SQLBinaryOpExpr sql) {
        sql.setLeft(new SQLIntegerExpr(1));
        sql.setOperator(SQLBinaryOperator.Equality);
        SQLBinaryOperator parentOperator = ((SQLBinaryOpExpr) sql.getParent()).getOperator();
        switch (parentOperator) {
            case BooleanOr:
                sql.setRight(new SQLIntegerExpr(2));
                break;
            default:
                sql.setRight(new SQLIntegerExpr(1));
        }
    }

    private boolean haveParamsCondition(List<String> notMustParams, String value, SQLBinaryOperator operator) {
        if (operator == SQLBinaryOperator.Like || operator == SQLBinaryOperator.NotLike) {
            return notMustParams.stream().anyMatch(value::contains);
        } else {
            return notMustParams.contains(value);
        }
    }

    public String buildSQLIdentifier(SQLExpr value) {
        if (value instanceof SQLVariantRefExpr) {
            return ((SQLVariantRefExpr) value).getName();
        }
        if (value instanceof SQLValuableExpr) {
            return ((SQLValuableExpr) value).getValue().toString();
        }
        return null;
    }


    public String buildSQLInListIdentifier(List<SQLExpr> value) {
        if (value != null && value.size() == 1) {
            SQLExpr itemValue = value.get(0);
            if (itemValue instanceof SQLVariantRefExpr) {
                return ((SQLVariantRefExpr) itemValue).getName();
            }
            if (itemValue instanceof SQLValuableExpr) {
                return ((SQLValuableExpr) itemValue).getValue().toString();
            }
        }
        return null;
    }

    /**
     * 判断该condition中的左右子查询都是 or 并且 每个子查询中都有参数
     *
     * @param sql
     * @param notMustParams
     * @return
     */
    public boolean isAllOrNotMustParams(SQLBinaryOpExpr sql, List<String> notMustParams) {
        SQLExpr left = sql.getLeft();
        SQLExpr right = sql.getRight();
        boolean leftAll = true;
        boolean rightAll = true;

        if (left instanceof SQLBinaryOpExpr) {
            if (notMustParams.stream().anyMatch(left.toString()::contains)) {
                leftAll = isAllOrNotMustParams((SQLBinaryOpExpr) left, notMustParams) && sql.getOperator() == BooleanOr;
            } else {
                leftAll = false;
            }
        }

        if (right instanceof SQLBinaryOpExpr) {
            if (notMustParams.stream().anyMatch(right.toString()::contains)) {
                rightAll = isAllOrNotMustParams((SQLBinaryOpExpr) right, notMustParams) && sql.getOperator() == BooleanOr;
            } else {
                rightAll = false;
            }
        }
        return leftAll && rightAll;

    }
}
