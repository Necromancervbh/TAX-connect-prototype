package com.example.taxconnect.utils;

import java.util.ArrayList;
import java.util.List;
import com.example.taxconnect.model.UserModel;

public class ListUtils {
    public static class SplitResult {
        public List<UserModel> topList;
        public List<UserModel> featuredList;

        public SplitResult(List<UserModel> topList, List<UserModel> featuredList) {
            this.topList = topList;
            this.featuredList = featuredList;
        }
    }

    public static SplitResult processCALists(List<UserModel> fullList) {
        if (fullList == null) {
            return new SplitResult(new ArrayList<>(), new ArrayList<>());
        }
        
        // Take top 5 for Top Rated
        List<UserModel> topList = new ArrayList<>();
        int limit = Math.min(fullList.size(), 5);
        for (int i = 0; i < limit; i++) {
            topList.add(fullList.get(i));
        }
        
        // Take next 5 for Featured
        List<UserModel> featuredList = new ArrayList<>();
        if (fullList.size() > 5) {
            int limitFeatured = Math.min(fullList.size(), 10);
            for (int i = 5; i < limitFeatured; i++) {
                featuredList.add(fullList.get(i));
            }
        }
        
        return new SplitResult(topList, featuredList);
    }
}
