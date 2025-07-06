package work;

import lombok.Builder;

@Builder
public class House {
    private String address;
    private int rooms;
    private String area;
    private double price;
}
