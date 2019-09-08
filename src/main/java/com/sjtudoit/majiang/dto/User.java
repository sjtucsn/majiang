package com.sjtudoit.majiang.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class User {
    private String userNickName;
    private Integer index;
    private Boolean canChi;
    private Boolean canPeng;
    private Boolean canGang;
    private Boolean canHu;
    private Boolean canQiangJin;
    private Boolean isBanker = false; // 是否为庄家
    private Boolean ready = false; // 是否已准备
    private Integer score = 100; // 分数，默认一开始都是100
    private Integer scoreChange = 0; // 分数变化
    private Integer stayNum = 0; // 占庄数

    private List<Majiang> userMajiangList = new ArrayList<>();
    private List<Majiang> outList = new ArrayList<>();

    private List<Majiang> flowerList = new ArrayList<>();

    public User() {}

    public User(String userNickName, Integer index) {
        this.userNickName = userNickName;
        this.index = index;
    }

    public User(Integer index, String userNickName, List<Majiang> userMajiangList) {
        this.index = index;
        this.userNickName = userNickName;
        this.userMajiangList = userMajiangList;
    }

    public String getUserNickName() {
        return userNickName;
    }

    public void setUserNickName(String userNickName) {
        this.userNickName = userNickName;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Boolean getCanChi() {
        return canChi;
    }

    public void setCanChi(Boolean canChi) {
        this.canChi = canChi;
    }

    public Boolean getCanPeng() {
        return canPeng;
    }

    public void setCanPeng(Boolean canPeng) {
        this.canPeng = canPeng;
    }

    public Boolean getCanGang() {
        return canGang;
    }

    public void setCanGang(Boolean canGang) {
        this.canGang = canGang;
    }

    public Boolean getCanHu() {
        return canHu;
    }

    public void setCanHu(Boolean canHu) {
        this.canHu = canHu;
    }

    public Boolean getBanker() {
        return isBanker;
    }

    public void setBanker(Boolean banker) {
        isBanker = banker;
    }

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public Boolean getCanQiangJin() {
        return canQiangJin;
    }

    public void setCanQiangJin(Boolean canQiangJin) {
        this.canQiangJin = canQiangJin;
    }

    public Integer getScoreChange() {
        return scoreChange;
    }

    public void setScoreChange(Integer scoreChange) {
        this.scoreChange = scoreChange;
    }

    public List<Majiang> getUserMajiangList() {
        return userMajiangList;
    }

    public void setUserMajiangList(List<Majiang> userMajiangList) {
        this.userMajiangList = userMajiangList;
    }

    public void addMajiang(Majiang majiang) {
        userMajiangList.add(majiang);
    }

    /**
     * 游戏开始时补花
     * @param remainMajiangList 剩余麻将列表
     */
    public void resetFlower(List<Majiang> remainMajiangList) {
        int userMajiangNum = userMajiangList.size();
        for (int i = 0; i < userMajiangNum; i++) {
            if (userMajiangList.get(i).getCode() > 30) {
                Majiang flower = userMajiangList.get(i);
                flowerList.add(flower);
                userMajiangList.set(i, remainMajiangList.remove(remainMajiangList.size() - 1));
            }
        }
        // 给麻将排序
        userMajiangList = userMajiangList.stream().sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList());
    }

    public Majiang removeMajiang(int index) {
        Majiang outMajiang = userMajiangList.remove(index);
        addOutList(outMajiang);
        return outMajiang;
    }

    public void addOutList(Majiang majiang) {
        outList.add(majiang);
    }

    public void removeLastOfOutList() {
        outList.remove(outList.size() - 1);
    }

    public void addFlowerList(Majiang majiang) {
        flowerList.add(majiang);
    }

    /**
     * 设置用户手牌中的哪张为金
     * @param code
     */
    public void setJin(Integer code) {
        for (int i = 0; i < userMajiangList.size(); i++) {
            if (userMajiangList.get(i).getCode().equals(code)) {
                Majiang majiangJin = userMajiangList.get(i);
                majiangJin.setJin(true);
                // userMajiangList.set(i, majiangJin);
            }
        }
    }

    public void sortMajiangList() {
        // 麻将显示顺序，先暗杠、再亮牌、再金、最后普通牌
        List<Majiang> tmpList = userMajiangList.stream().filter(Majiang::isAnGang).collect(Collectors.toList());
        List<Majiang> showList = userMajiangList.stream().filter(Majiang::isShow).collect(Collectors.toList());
        List<Majiang> jinList = userMajiangList.stream().filter(Majiang::isJin).collect(Collectors.toList());
        tmpList.addAll(showList);
        tmpList.addAll(jinList);
        tmpList.addAll(userMajiangList.stream().filter(majiang -> !majiang.isShow() && !majiang.isAnGang() && !majiang.isJin()).sorted(Comparator.comparing(Majiang::getId)).collect(Collectors.toList()));
        userMajiangList = tmpList;
    }

    public void setMajiangChi(Integer id1, Integer id2, Integer id3) {
        for (int i = 0; i < userMajiangList.size(); i++) {
            Majiang majiang = userMajiangList.get(i);
            if (majiang.getId().equals(id1) || majiang.getId().equals(id2) || majiang.getId().equals(id3)) {
                majiang.setShow(Boolean.TRUE);
                // userMajiangList.set(i, majiang);
            }
        }
    }

    public void setMajiangPeng(Integer code) {
        for (int i = 0; i < userMajiangList.size() - 2; i++) {
            Majiang majiang1 = userMajiangList.get(i);
            Majiang majiang2 = userMajiangList.get(i + 1);
            Majiang majiang3 = userMajiangList.get(i + 2);
            if (majiang1.getCode().equals(code) && majiang2.getCode().equals(code) && majiang3.getCode().equals(code)) {
                majiang1.setShow(Boolean.TRUE);
                majiang2.setShow(Boolean.TRUE);
                majiang3.setShow(Boolean.TRUE);
                // userMajiangList.set(i, majiang1);
                // userMajiangList.set(i + 1, majiang2);
                // userMajiangList.set(i + 2, majiang3);
                return;
            }
        }
    }

    public void setMajiangGang(Integer code) {
        for (int i = 0; i < userMajiangList.size() - 3; i++) {
            Majiang majiang1 = userMajiangList.get(i);
            Majiang majiang2 = userMajiangList.get(i + 1);
            Majiang majiang3 = userMajiangList.get(i + 2);
            Majiang majiang4 = userMajiangList.get(i + 3);
            if (majiang1.getCode().equals(code) && majiang2.getCode().equals(code) && majiang3.getCode().equals(code) && majiang4.getCode().equals(code)) {
                majiang1.setShow(true);
                majiang2.setShow(true);
                majiang3.setShow(true);
                majiang4.setShow(true);
                // userMajiangList.set(i, majiang1);
                // userMajiangList.set(i + 1, majiang2);
                // userMajiangList.set(i + 2, majiang3);
                // userMajiangList.set(i + 3, majiang4);
                return;
            }
        }
    }

    public void setMajiangAnGang(Integer code) {
        for (int i = 0; i < userMajiangList.size() - 3; i++) {
            Majiang majiang1 = userMajiangList.get(i);
            Majiang majiang2 = userMajiangList.get(i + 1);
            Majiang majiang3 = userMajiangList.get(i + 2);
            Majiang majiang4 = userMajiangList.get(i + 3);
            if (majiang1.getCode().equals(code) && majiang2.getCode().equals(code) && majiang3.getCode().equals(code) && majiang4.getCode().equals(code)) {
                majiang1.setAnGang(true);
                majiang2.setAnGang(true);
                majiang3.setAnGang(true);
                majiang4.setAnGang(true);
                // userMajiangList.set(i, majiang1);
                // userMajiangList.set(i + 1, majiang2);
                // userMajiangList.set(i + 2, majiang3);
                // userMajiangList.set(i + 3, majiang4);
                return;
            }
        }
    }

    public void setMajiangJiaGang(Integer code) {
        for (int i = 0; i < userMajiangList.size(); i++) {
            if (!userMajiangList.get(i).isShow() && userMajiangList.get(i).getCode().equals(code)) {
                Majiang majiang = userMajiangList.get(i);
                majiang.setShow(Boolean.TRUE);
                // userMajiangList.set(i, majiang);
                return;
            }
        }
    }

    public void setMajiangHu() {
        for (int i = 0; i < userMajiangList.size(); i++) {
            Majiang majiang = userMajiangList.get(i);
            majiang.setShow(true);
            majiang.setAnGang(false); // 取消暗杠的显示
            // userMajiangList.set(i, majiang);
        }
    }

    public Boolean needAddMajiang() {
        return userMajiangList.stream().filter(majiang -> !majiang.isAnGang() && !majiang.isShow()).count() % 3 == 1;
    }

    public List<Majiang> getOutList() {
        return outList;
    }

    public void setOutList(List<Majiang> outList) {
        this.outList = outList;
    }

    public List<Majiang> getFlowerList() {
        return flowerList;
    }

    public void setFlowerList(List<Majiang> flowerList) {
        this.flowerList = flowerList;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getStayNum() {
        return stayNum;
    }

    public void setStayNum(Integer stayNum) {
        this.stayNum = stayNum;
    }
}
