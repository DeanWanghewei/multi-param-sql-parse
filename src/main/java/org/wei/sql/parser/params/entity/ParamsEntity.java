package org.wei.sql.parser.params.entity;

public class ParamsEntity {
    /**
     * 参数名称
     */
    public String paramsName;
    /**
     * 是否必填
     */
    public boolean must;
    /**
     * 参数类型
     */
    public String paramsType;

    public ParamsEntity(String paramsName, boolean must, String paramsType) {
        this.paramsName = paramsName;
        this.must = must;
        this.paramsType = paramsType;
    }

    public String getParamsName() {
        return paramsName;
    }

    public void setParamsName(String paramsName) {
        this.paramsName = paramsName;
    }

    public boolean isMust() {
        return must;
    }

    public void setMust(boolean must) {
        this.must = must;
    }

    public String getParamsType() {
        return paramsType;
    }

    public void setParamsType(String paramsType) {
        this.paramsType = paramsType;
    }
}
