package TestAgent;

public class MainAuction {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			logist.LogistPlatform.main(args);
		} else {
			String[] defaultArgs = { "config/auction.xml", "auction-our", "auction-test"};
			logist.LogistPlatform.main(defaultArgs);
		}
	}
}
