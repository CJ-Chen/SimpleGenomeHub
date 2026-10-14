package simplegenomehub.genomebrowser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** NCBI translation tables ported from PlastYes-Kuai's CodonTables. */
public final class CodonTable {

    public static final int[] NUMBERS = {1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 33};
    public static final String[] NAMES = {
        "1 Standard",
        "2 Vertebrate Mitochondrial",
        "3 Yeast Mitochondrial",
        "4 Mold/Protozoan/Coelenterate Mitochondrial; Mycoplasma/Spiroplasma",
        "5 Invertebrate Mitochondrial",
        "6 Ciliate/Dasycladacean/Hexamita Nuclear",
        "9 Echinoderm and Flatworm Mitochondrial",
        "10 Euplotid Nuclear",
        "11 Bacterial, Archaeal and Plant Plastid",
        "12 Alternative Yeast Nuclear",
        "13 Ascidian Mitochondrial",
        "14 Alternative Flatworm Mitochondrial",
        "15 Blepharisma Macronuclear",
        "16 Chlorophycean Mitochondrial",
        "21 Trematode Mitochondrial",
        "22 Scenedesmus obliquus Mitochondrial",
        "23 Thraustochytrium Mitochondrial",
        "24 Pterobranchia Mitochondrial",
        "25 Candidate Division SR1 and Gracilibacteria",
        "26 Pachysolen tannophilus Nuclear",
        "27 Karyorelict Nuclear",
        "28 Condylostoma Nuclear",
        "29 Mesodinium Nuclear",
        "30 Peritrich Nuclear",
        "31 Blastocrithidia Nuclear",
        "33 Cephalodiscidae Mitochondrial UAA-Tyr"};

    private static final Map<Integer, String> AMINO = new LinkedHashMap<>();
    private static final Map<Integer, String> STARTS = new LinkedHashMap<>();

    static {
        AMINO.put(1, "FFLLSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(1, "---M---------------M---------------M----------------------------");
        AMINO.put(2, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSS**VVVVAAAADDEEGGGG");
        STARTS.put(2, "--------------------------------MMMM---------------M------------");
        AMINO.put(3, "FFLLSSSSYY**CCWWTTTTPPPPHHQQRRRRIIMMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(3, "----------------------------------MM---------------M------------");
        AMINO.put(4, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(4, "--MM---------------M------------MMMM---------------M------------");
        AMINO.put(5, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSSSSVVVVAAAADDEEGGGG");
        STARTS.put(5, "---M------------------------------MM---------------M------------");
        AMINO.put(6, "FFLLSSSSYYQQCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(6, "-----------------------------------M----------------------------");
        AMINO.put(9, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNNKSSSSVVVVAAAADDEEGGGG");
        STARTS.put(9, "-----------------------------------M---------------M------------");
        AMINO.put(10, "FFLLSSSSYY**CCCWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(10, "-----------------------------------M----------------------------");
        AMINO.put(11, "FFLLSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(11, "---M---------------M------------MMMM---------------M------------");
        AMINO.put(12, "FFLLSSSSYY**CC*WLLLSPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(12, "-------------------M---------------M----------------------------");
        AMINO.put(13, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSSGGVVVVAAAADDEEGGGG");
        STARTS.put(13, "---M------------------------------MM---------------M------------");
        AMINO.put(14, "FFLLSSSSYYY*CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNNKSSSSVVVVAAAADDEEGGGG");
        STARTS.put(14, "-----------------------------------M----------------------------");
        AMINO.put(15, "FFLLSSSSYY*QCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(15, "-----------------------------------M----------------------------");
        AMINO.put(16, "FFLLSSSSYY*LCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(16, "-----------------------------------M----------------------------");
        AMINO.put(21, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNNKSSSSVVVVAAAADDEEGGGG");
        STARTS.put(21, "-----------------------------------M---------------M------------");
        AMINO.put(22, "FFLLSS*SYY*LCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(22, "-----------------------------------M----------------------------");
        AMINO.put(23, "FF*LSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(23, "--------------------------------M--M---------------M------------");
        AMINO.put(24, "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSSKVVVVAAAADDEEGGGG");
        STARTS.put(24, "---M---------------M---------------M---------------M------------");
        AMINO.put(25, "FFLLSSSSYY**CCGWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(25, "---M-------------------------------M---------------M------------");
        AMINO.put(26, "FFLLSSSSYY**CC*WLLLAPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(26, "-------------------M---------------M----------------------------");
        AMINO.put(27, "FFLLSSSSYYQQCCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(27, "-----------------------------------M----------------------------");
        AMINO.put(28, "FFLLSSSSYYQQCCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(28, "-----------------------------------M----------------------------");
        AMINO.put(29, "FFLLSSSSYYYYCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(29, "-----------------------------------M----------------------------");
        AMINO.put(30, "FFLLSSSSYYEECC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(30, "-----------------------------------M----------------------------");
        AMINO.put(31, "FFLLSSSSYYEECCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG");
        STARTS.put(31, "-----------------------------------M----------------------------");
        AMINO.put(33, "FFLLSSSSYYY*CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSSKVVVVAAAADDEEGGGG");
        STARTS.put(33, "---M---------------M---------------M---------------M------------");
    }

    private CodonTable() {
    }

    public static boolean supports(int table) {
        return AMINO.containsKey(table);
    }

    public static char aminoAcid(int table, String codon) {
        int index = index(codon);
        if (index < 0) return 'X';
        return AMINO.getOrDefault(table, AMINO.get(1)).charAt(index);
    }

    public static String translate(int table, String sequence) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i + 3 <= sequence.length(); i += 3) {
            result.append(aminoAcid(table, sequence.substring(i, i + 3)));
        }
        return result.toString();
    }

    public static boolean isStart(int table, String codon) {
        int index = index(codon);
        return index >= 0 && STARTS.getOrDefault(table, STARTS.get(1)).charAt(index) == 'M';
    }

    private static int index(String codon) {
        if (codon == null || codon.length() != 3) return -1;
        int index = 0;
        for (int i = 0; i < 3; i++) {
            char base = Character.toUpperCase(codon.charAt(i));
            int digit = "TCAG".indexOf(base == 'U' ? 'T' : base);
            if (digit < 0) return -1;
            index = index * 4 + digit;
        }
        return index;
    }
}
