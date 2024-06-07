package com.PlayAsElf;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class FetchedModelInfo {

    private final int[] modelIds;
    private final short[] recolorFrom;
    private final short[] recolorTo;
    //private final int maleOffset;
    //private final int femaleOffset;
}
