package edu.mit.csail.cgs.warpdrive.model;

import edu.mit.csail.cgs.datasets.general.Region;
import edu.mit.csail.cgs.ewok.verbs.Expander;

public class RegionMultipleExpanderModel<OUT> extends MultipleExpanderModel<Region,OUT> implements RegionModel {
    private Region region;

    public RegionMultipleExpanderModel() {
    }

    public void setRegion(Region r) throws NullPointerException {
        if (r == null) {throw new NullPointerException("Region can't be null");}
        region = r;
        setInput(r);
    }
    
    public Region getRegion() {
        return region;
    }
}