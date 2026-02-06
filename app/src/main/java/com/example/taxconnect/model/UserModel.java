package com.example.taxconnect.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UserModel implements Serializable {
    private String uid;
    private String email;
    private String name;
    private String role; // "CUSTOMER" or "CA"
    private String city;
    private boolean isOnline;
    private double rating;
    private int ratingCount;
    private List<String> blockedUsers;
    private String bio;

    // CA Specific Fields
    private String caNumber;
    private String experience;
    private String specialization;
    private String minCharges;
    private int clientCount;

    // Customer Specific Fields
    private String priceRange;
    
    private String profileImageUrl;
    private String introVideoUrl;
    private String fcmToken;
    private java.util.List<CertificateModel> certificates;
    private double walletBalance;
    private boolean isVerified;
    private boolean isVerificationRequested;
    private String phoneNumber;

    public UserModel() {
        // Required for Firestore
        this.blockedUsers = new ArrayList<>();
        this.walletBalance = 0.0;
        this.isVerified = false;
        this.phoneNumber = "";
    }

    public UserModel(String uid, String email, String name, String role) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.role = role;
        this.blockedUsers = new ArrayList<>();
        this.isOnline = false; // Default offline
        this.rating = 0.0;
        this.ratingCount = 0;
        this.walletBalance = 0.0;
        this.isVerified = false;
        this.phoneNumber = "";
    }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public boolean isVerificationRequested() { return isVerificationRequested; }
    public void setVerificationRequested(boolean verificationRequested) { isVerificationRequested = verificationRequested; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public List<String> getBlockedUsers() { return blockedUsers; }
    public void setBlockedUsers(List<String> blockedUsers) { this.blockedUsers = blockedUsers; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCaNumber() { return caNumber; }
    public void setCaNumber(String caNumber) { this.caNumber = caNumber; }

    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getMinCharges() { return minCharges; }
    public void setMinCharges(String minCharges) { this.minCharges = minCharges; }

    public int getClientCount() { return clientCount; }
    public void setClientCount(int clientCount) { this.clientCount = clientCount; }

    public String getPriceRange() { return priceRange; }
    public void setPriceRange(String priceRange) { this.priceRange = priceRange; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getIntroVideoUrl() { return introVideoUrl; }
    public void setIntroVideoUrl(String introVideoUrl) { this.introVideoUrl = introVideoUrl; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public double getWalletBalance() { return walletBalance; }
    public void setWalletBalance(double walletBalance) { this.walletBalance = walletBalance; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }



    public List<CertificateModel> getCertificates() {
        return certificates;
    }
    public void setCertificates(java.util.List<CertificateModel> certificates) { this.certificates = certificates; }
}
