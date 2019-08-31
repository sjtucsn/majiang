package com.sjtudoit.majiang.dto;

public class Majiang {
    private Integer id;
    private Integer code;
    private String name;

    private Boolean jin;
    private Boolean show;
    private Boolean anGang;

    public Majiang() {
    }

    public Majiang(Integer id, Integer code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.jin = false;
        this.show = false;
        this.anGang = false;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean isJin() {
        return jin;
    }

    public void setJin(Boolean jin) {
        this.jin = jin;
    }

    public Boolean isShow() {
        return show;
    }

    public void setShow(Boolean show) {
        this.show = show;
    }

    public Boolean isAnGang() {
        return anGang;
    }

    public void setAnGang(Boolean anGang) {
        this.anGang = anGang;
    }

    @Override
    public String toString() {
        return "Majiang{" +
                "id=" + id +
                ", code=" + code +
                ", name='" + name + '\'' +
                ", jin=" + jin +
                ", show=" + show +
                ", anGang=" + anGang +
                '}';
    }
}
