package com.exampl.antiaddiction.model;

public class UserInfo {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String role;
    private Long boundUserId;
    public  UserInfo(String username,String password,String role){
        this.username=username;
        this.password=password;
        this.role=role;
    }
    public Long getBoundUserId() {
        return boundUserId;
    }

    public void setBoundUserId(Long boundUserId) {
        this.boundUserId = boundUserId;
    }


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }


}
