package blue.endless.jankson;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import org.junit.Assert;
import org.junit.Test;

public class DFUTests {

    private static class Data {
        public int a = 5;
        public int b = 6;
    }

    @Test
    public void blankDfuTest() {
        DataFixer dataFixer = new DataFixerBuilder(0).build().fixer();
        Jankson jankson = Jankson.builder().withFixer(dataFixer).withVersion(0).build();

        JsonElement data = jankson.toJson(new Data());
        String str = data.toJson();
        Assert.assertEquals("{ \"jankson:schema_version\": 0, \"a\": 5, \"b\": 6 }", str);
    }
}
