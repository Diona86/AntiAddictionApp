package com.exampl.antiaddiction.api;

import com.exampl.antiaddiction.model.UserInfo;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ApiService {
    @GET("api/users/{id}")
    Call<UserInfo> getUser(@Path("id") long id);
}
