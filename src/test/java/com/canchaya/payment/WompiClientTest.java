package com.canchaya.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WompiClientTest {

    private final WompiClient wompi = new WompiClient("pub_test_x", "test_integrity_abc", "test_events_xyz",
            "https://sandbox.wompi.co/v1");

    @Test
    void integritySignatureIsSha256OfReferenceCentsCurrencyAndSecret() {
        assertThat(wompi.integritySignature("RES-7-1700000000000", 4_800_000, "COP"))
                .isEqualTo("92d84b4da583124372923138b348c2d28d31fd7e2a36b392705be2ddb20e4b03");
    }

    @Test
    void webhookChecksumIsVerified() throws Exception {
        String event = """
                {"event":"transaction.updated",
                 "data":{"transaction":{"id":"1234-1610641025-49201","status":"APPROVED","amount_in_cents":4800000}},
                 "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],
                              "checksum":"%s"},
                 "timestamp":1530291411}
                """;
        ObjectMapper json = new ObjectMapper();
        JsonNode good = json.readTree(event.formatted("7851A783D482F714F2EFCA407B898044E076400DE4586FF7BE50AF7235A1BCED"));
        JsonNode tampered = json.readTree(event.formatted("7851a783d482f714f2efca407b898044e076400de4586ff7be50af7235a1bced")
                .replace("APPROVED", "DECLINED"));

        assertThat(wompi.isAuthentic(good)).isTrue();
        assertThat(wompi.isAuthentic(tampered)).isFalse();
    }

    @Test
    void keysPastedWithSpacesOrNewlinesStillSignTheSame() {
        WompiClient pasted = new WompiClient(" pub_test_x\n", "test_integrity_abc \n", "\ttest_events_xyz ",
                "https://sandbox.wompi.co/v1");

        assertThat(pasted.publicKey()).isEqualTo("pub_test_x");
        assertThat(pasted.integritySignature("RES-7-1700000000000", 4_800_000, "COP"))
                .isEqualTo(wompi.integritySignature("RES-7-1700000000000", 4_800_000, "COP"));
    }

    @Test
    void keyKindIsDetectedWithoutLookingAtTheSecret() {
        assertThat(WompiClient.kind("test_integrity_abc", "test_integrity_", "prod_integrity_")).isEqualTo("PRUEBAS");
        assertThat(WompiClient.kind("prod_integrity_abc", "test_integrity_", "prod_integrity_")).isEqualTo("PRODUCCION");
        assertThat(WompiClient.kind("", "test_integrity_", "prod_integrity_")).isEqualTo("VACIA");
        assertThat(WompiClient.kind("abc", "test_integrity_", "prod_integrity_")).isEqualTo("FORMATO DESCONOCIDO");
    }
}
