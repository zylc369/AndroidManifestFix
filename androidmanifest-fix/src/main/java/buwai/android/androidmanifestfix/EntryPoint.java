package buwai.android.androidmanifestfix;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;
import pxb.android.axml.Util;

public class EntryPoint {

	private static Options options;

	static {
		options = new Options();
		options.addOption("h", false, "使用帮助");
		options.addOption("s", true, "输入文件");
		options.addOption("o", true, "输出文件");
	}

	public static void main(String[] args) {
		try {
			BasicParser parser = new BasicParser();
			CommandLine cl = parser.parse(options, args);

			if (cl.hasOption('h')) {
				usage();
			}

			// dex路径。
			File in = null;
			if (cl.hasOption('s')) {
				in = new File(cl.getOptionValue('s'));
			}
			// 输出目录。
			File out = null;
			if (cl.hasOption('o')) {
				out = new File(cl.getOptionValue('o'));
			}
			if (null == in || null == out) {
				usage();
			}

			System.out.println("------ 开始修复 ------");
			fix(in, out);
			System.out.println("------ 修复完成 ------");
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void fix(File in, File out) throws IOException {
		AxmlReader reader = new AxmlReader(Util.readFile(in));
		AxmlWriter writer = new AxmlWriter();

		try { // write non-utf8 string for all platform
			Field fStringItems = AxmlWriter.class.getDeclaredField("stringItems");
			fStringItems.setAccessible(true);
			Object stringItems = fStringItems.get(writer);
			Field fuseUTF8 = stringItems.getClass().getDeclaredField("useUTF8");
			fuseUTF8.setAccessible(true);
			fuseUTF8.setBoolean(stringItems, false);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		reader.accept(new AxmlVisitor(writer) {

			@Override
			public void attr(String ns, String name, int resourceId, int type, Object obj) {
				System.out.println(String.format("ns:%s, name:%s, resourceId:%d, type:%d, obj:%s", ns, name,
						resourceId, type, obj.toString()));
				super.attr(ns, name, resourceId, type, obj);
			}

			@Override
			public NodeVisitor child(String ns, String name) {// manifest
				NodeVisitor nv = super.child(ns, name);
				return new NodeVisitor(nv) {

					@Override
					public void attr(String ns, String name, int resourceId, int type, Object obj) {
						// System.out.println(String.format("ns:%s, name:%s, resourceId:%d-%s, type:%d-%s, obj:%s",
						// ns,
						// name, resourceId, Integer.toHexString(resourceId),
						// type, Integer.toHexString(type),
						// obj.toString()));
						String realName = -1 == resourceId ? name : AxmlNameHelper.get(resourceId).getName();
						String realNs = null == ns ? null : AxmlNameHelper.ns;

						// System.out.println(String.format("ns:%s, name:%s, resourceId:%d-%s, type:%d-%s, obj:%s",
						// ns,
						// realName, resourceId,
						// Integer.toHexString(resourceId), type,
						// Integer.toHexString(type),
						// obj.toString()));
						super.attr(realNs, realName, resourceId, type, obj);
						// super.attr(ns, name, resourceId, type, obj);
					}

					@Override
					public NodeVisitor child(String ns, String name) {// application
						if (name.equals("uses-sdk") || name.equals("uses-permission")) {
							// 修复uses-sdk、uses-permission标签的属性。
							return new NodeVisitor(super.child(ns, name)) {

								public void attr(String ns, String name, int resourceId, int type, Object obj) {
									String realName = -1 == resourceId ? name : AxmlNameHelper.get(resourceId)
											.getName();
									String realNs = null == ns ? null : AxmlNameHelper.ns;
									super.attr(realNs, realName, resourceId, type, obj);
								}
							};
						} else if (name.equals("application")) {
							// 修复application标签的属性。
							return new NodeVisitor(super.child(ns, name)) {

								public void attr(String ns, String name, int resourceId, int type, Object obj) {
									String realName = -1 == resourceId ? name : AxmlNameHelper.get(resourceId)
											.getName();
									String realNs = null == ns ? null : AxmlNameHelper.ns;
									super.attr(realNs, realName, resourceId, type, obj);
								}

								@Override
								public NodeVisitor child(String ns, String name) {
									if (name.equals("activity") || name.equals("service") || name.equals("receiver")
											|| name.equals("provider")) {
										// 修复activity、service、receiver标签的属性。
										return new NodeVisitor(super.child(ns, name)) {
											@Override
											public void attr(String ns, String name, int resourceId, int type,
													Object obj) {
												String realName = -1 == resourceId ? name : AxmlNameHelper.get(
														resourceId).getName();
												String realNs = null == ns ? null : AxmlNameHelper.ns;
												super.attr(realNs, realName, resourceId, type, obj);
											}

											public NodeVisitor child(String ns, String name) {
												if (name.equals("intent-filter") || name.equals("meta-data")) {
													// 修复intent-filter、meta-data标签的属性。
													return new NodeVisitor(super.child(ns, name)) {
														public void attr(String ns, String name, int resourceId,
																int type, Object obj) {
															String realName = -1 == resourceId ? name : AxmlNameHelper
																	.get(resourceId).getName();
															String realNs = null == ns ? null : AxmlNameHelper.ns;
															super.attr(realNs, realName, resourceId, type, obj);
														}
													};
												}
												return super.child(ns, name);
											}
										};
									}
									return super.child(ns, name);
								}
							};
						}
						return super.child(ns, name);
					}
				};
			}
		});

		Util.writeFile(writer.toByteArray(), out);
	}

	/**
	 * 用法。
	 */
	private static void usage() {
		HelpFormatter help = new HelpFormatter();
		help.printHelp("-s <输入文件> -o <输出文件>", options);
		System.exit(-1);
	}

}
