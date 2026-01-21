package com.avispl.symphony.dal.communicator.data;

public interface Constant {
    public interface URI {
        public String DEVICES = "v1/devices?nextToken=%s&limit=%s";
        public String DEVICE = "v1/devices/%s";
        public String AUTH = "/oauth2/token";
    }
}
