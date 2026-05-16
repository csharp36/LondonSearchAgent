package com.londonsearch.alert;

import com.londonsearch.model.Property;

import java.util.List;

public interface AlertService {
    void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkUrl);
}
