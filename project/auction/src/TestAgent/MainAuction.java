package TestAgent;

public class MainAuction {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			logist.LogistPlatform.main(args);
		} else {
			String[] defaultArgs = {"config/auction.xml", "auction-main-51", "auction-main-51"};
			logist.LogistPlatform.main(defaultArgs);
		}
	}
}
