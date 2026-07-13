package com.nantaaditya.sotres.helper;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Transaction;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import java.io.File;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class BenchmarkJsltTest {

  private ObjectMapper mapper;
  private RequestContext requestContext;
  private Expression jsltExpression;

  @Setup
  public void setup() {
    mapper = new ObjectMapper();
    requestContext = RequestContext.builder()
        .mti("0100")
        .cardNo("1234567890123456")
        .processingCode("000000")
        .stan("123456")
        .lateResponse(false)
        .transaction(Transaction.builder()
            .originalAmount(BigDecimal.valueOf(Integer.MAX_VALUE))
            .originalCurrencyCode("360")
            .build())
        .merchant(
            Merchant.builder()
                .merchantName("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
                .merchantCategoryCode("0000")
                .build()
        )
        .build();

    File scriptFile = new File("src/test/resources/example.jslt");
    jsltExpression = Parser.compile(scriptFile);
  }

  @Benchmark
  public String directSerialization() throws Exception {
    return mapper.writeValueAsString(requestContext);
  }

  @Benchmark
  public String jsltTransformationAndSerialization() throws Exception {
    JsonNode rootNode = mapper.valueToTree(requestContext);
    JsonNode transformedNode = jsltExpression.apply(rootNode);
    return mapper.writeValueAsString(transformedNode);
  }

  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .include(BenchmarkJsltTest.class.getSimpleName())
        .build();
    new Runner(opt).run();
  }
}
