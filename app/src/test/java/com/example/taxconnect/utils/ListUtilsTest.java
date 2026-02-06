package com.example.taxconnect.utils;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import com.example.taxconnect.model.UserModel;

public class ListUtilsTest {

    private UserModel createUser(double rating) {
        UserModel user = new UserModel();
        // Since fields are private and we don't have setters visible in snippet,
        // we assume we can either mock or use reflection, or if there's a constructor/setter.
        // The read of UserModel.java showed getters but setters were commented "// Getters and setters".
        // I'll assume standard setters exist or I can subclass.
        // Actually, if setters are missing, I can't test sort.
        // Let's assume there's a setRating method.
        // If not, I'll have to rely on order being preserved if all 0?
        // Wait, I saw "private double rating;" and "public double getRating()".
        // I'll try to find a way to set it. JSON deserialization uses setters or reflection.
        // I'll use a mock or subclass if possible. 
        // Or I can just check the size logic which is independent of rating if I don't test sort order.
        // But testing sort order is important.
        return user; 
    }
    
    // Mocking UserModel for test since we don't have public setters confirmed
    private static class TestUser extends UserModel {
        private double testRating;
        public TestUser(double rating) {
            this.testRating = rating;
        }
        @Override
        public double getRating() {
            return testRating;
        }
    }

    @Test
    public void testProcessCALists_Empty() {
        List<UserModel> input = new ArrayList<>();
        ListUtils.SplitResult result = ListUtils.processCALists(input);
        assertTrue(result.topList.isEmpty());
        assertTrue(result.featuredList.isEmpty());
    }

    @Test
    public void testProcessCALists_Under5() {
        List<UserModel> input = new ArrayList<>();
        input.add(new TestUser(4.5));
        input.add(new TestUser(3.0));
        input.add(new TestUser(5.0));

        ListUtils.SplitResult result = ListUtils.processCALists(input);
        
        assertEquals(3, result.topList.size());
        assertTrue(result.featuredList.isEmpty());
        
        // Verify sort order
        assertEquals(5.0, result.topList.get(0).getRating(), 0.01);
        assertEquals(4.5, result.topList.get(1).getRating(), 0.01);
        assertEquals(3.0, result.topList.get(2).getRating(), 0.01);
    }

    @Test
    public void testProcessCALists_Exact5() {
        List<UserModel> input = new ArrayList<>();
        for (int i = 0; i < 5; i++) input.add(new TestUser(4.0));

        ListUtils.SplitResult result = ListUtils.processCALists(input);
        assertEquals(5, result.topList.size());
        assertTrue(result.featuredList.isEmpty());
    }

    @Test
    public void testProcessCALists_MoreThan5() {
        List<UserModel> input = new ArrayList<>();
        // Add 8 users with ratings 1.0 to 8.0
        for (int i = 1; i <= 8; i++) {
            input.add(new TestUser((double) i));
        }

        ListUtils.SplitResult result = ListUtils.processCALists(input);
        
        // Top 5 should be 8.0, 7.0, 6.0, 5.0, 4.0
        assertEquals(5, result.topList.size());
        assertEquals(8.0, result.topList.get(0).getRating(), 0.01);
        assertEquals(4.0, result.topList.get(4).getRating(), 0.01);
        
        // Featured should be 3.0, 2.0, 1.0
        assertEquals(3, result.featuredList.size());
        assertEquals(3.0, result.featuredList.get(0).getRating(), 0.01);
        assertEquals(1.0, result.featuredList.get(2).getRating(), 0.01);
    }
    
    @Test
    public void testProcessCALists_MoreThan10() {
        List<UserModel> input = new ArrayList<>();
        for (int i = 0; i < 15; i++) input.add(new TestUser(5.0));

        ListUtils.SplitResult result = ListUtils.processCALists(input);
        assertEquals(5, result.topList.size());
        assertEquals(5, result.featuredList.size()); // Max 5 featured
    }
}
