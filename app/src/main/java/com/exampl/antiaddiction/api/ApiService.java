package com.exampl.antiaddiction.api;

import com.exampl.antiaddiction.model.Result;
import com.exampl.antiaddiction.model.UserInfo;
import com.google.firebase.firestore.auth.User;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ApiService {
    @GET("api/users/{id}")
    Call<UserInfo> getUser(@Path("id") long id);
    @POST("api/users/register")
    Call<Result<UserInfo>> registerUser(@Body UserInfo userInfo);
    @POST("api/users/login")
    Call<Result<UserInfo>> login(@Body UserInfo userInfo);
}
