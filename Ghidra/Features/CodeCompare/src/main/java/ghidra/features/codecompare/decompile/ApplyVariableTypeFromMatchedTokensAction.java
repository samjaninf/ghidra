/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.features.codecompare.decompile;

import docking.action.MenuData;
import ghidra.app.decompiler.ClangVariableToken;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.HelpLocation;
import ghidra.util.Msg;
import ghidra.util.datastruct.Duo.Side;
import ghidra.util.exception.*;

/**
 * An action for transferring the type of a local or global variable between matched tokens.
 */
public class ApplyVariableTypeFromMatchedTokensAction extends AbstractMatchedTokensAction {
	private PluginTool tool;
	public static final String ACTION_NAME = "Function Comparison Apply Variable Type";
	private static final String MENU_GROUP = "A1_ApplyVariable";

	/**
	 * Construtor
	 * @param diffPanel diff panel
	 * @param tool tool
	 */
	public ApplyVariableTypeFromMatchedTokensAction(DecompilerCodeComparisonPanel diffPanel,
			PluginTool tool) {
		super(ACTION_NAME, tool.getName(), diffPanel, true);
		this.tool = tool;

		MenuData menuData =
			new MenuData(new String[] { MENU_PARENT, "Variable Type" }, null, MENU_GROUP);
		setPopupMenuData(menuData);
		setHelpLocation(new HelpLocation(HELP_TOPIC, getName()));
	}

	@Override
	protected boolean isEnabledForDualDecompilerContext(DualDecompilerActionContext context) {
		TokenPair tokenPair = context.getTokenPair();

		if (tokenPair == null) {
			return false;
		}

		if (tokenPair.leftToken() == null || tokenPair.rightToken() == null) {
			return false;
		}

		if (!(tokenPair.leftToken() instanceof ClangVariableToken leftVar) ||
			!(tokenPair.rightToken() instanceof ClangVariableToken rightVar)) {
			return false;
		}

		HighSymbol leftSymbol = leftVar.getHighSymbol(context.getHighFunction(Side.LEFT));
		HighSymbol rightSymbol = rightVar.getHighSymbol(context.getHighFunction(Side.RIGHT));

		return leftSymbol != null && rightSymbol != null;
	}

	@Override
	public void dualDecompilerActionPerformed(DualDecompilerActionContext context) {
		TokenPair currentPair = context.getTokenPair();

		Side activeSide = diffPanel.getActiveSide();

		ClangVariableToken activeToken =
			activeSide == Side.LEFT ? (ClangVariableToken) currentPair.leftToken()
					: (ClangVariableToken) currentPair.rightToken();
		ClangVariableToken otherToken =
			activeSide == Side.LEFT ? (ClangVariableToken) currentPair.rightToken()
					: (ClangVariableToken) currentPair.leftToken();

		HighSymbol activeHighSymbol =
			activeToken.getHighSymbol(context.getHighFunction(activeSide));
		HighSymbol otherHighSymbol =
			otherToken.getHighSymbol(context.getHighFunction(activeSide.otherSide()));

		Function activeFunction = context.getCodeComparisonPanel().getFunction(activeSide);
		Program activeProgram = activeFunction.getProgram();

		DataType dt = otherHighSymbol.getDataType();

		try {
			activeProgram.withTransaction("Code Comparison Transfer Local Type", () -> {
				DataType resolvedDt = activeProgram.getDataTypeManager().resolve(dt, null);
				HighFunctionDBUtil.updateDBVariable(activeHighSymbol, null, resolvedDt,
					SourceType.IMPORTED);
			});
		}
		catch (InvalidInputException e) {
			Msg.showError(this, tool.getToolFrame(), "Type Transfer Failed",
				"Could not bring type " + dt.getName() + " to " + activeProgram.getName());
		}
		catch (UsrException e) {
			throw new AssertException("Unexpected exception", e);
		}
	}
}
