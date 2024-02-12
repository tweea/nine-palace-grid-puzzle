/*
 * 版权所有 2024 Tweea。
 * 保留所有权利。
 */
package cn.tweea.puzzle;

import java.io.Serializable;

/**
 * 后台给前台返回的结果。
 */
public class ResultDto<T>
    implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 结果代码。成功是 0 失败是非 0。
     */
    private Integer code;

    /**
     * 后台需要给前台的信息提示。如失败后，应该设置失败的具体原因。
     */
    private String message;

    /**
     * 返回的结果。
     */
    private T result;

    public ResultDto() {
        // 用于反序列化
    }

    public ResultDto(Integer code, String message, T result) {
        this.code = code;
        this.message = message;
        this.result = result;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
