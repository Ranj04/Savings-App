package handler;

import request.ParsedRequest;

public class HandlerFactory {
    // routes based on the path. Add your custom handlers here
    public static BaseHandler getHandler(ParsedRequest request) {
        return switch (request.getPath()) {
            case "/createUser", "/auth/createUser", "/auth/register" -> new CreateUserHandler();
            case "/login", "/auth/login" -> new LoginHandler();
            case "/logout", "/auth/logout" -> new LogoutHandler();
            case "/auth/whoami" -> new WhoAmIHandler();                 // <— NEW
            case "/getTransactions" -> new GetTransactionsHandler();
            case "/transactions" -> new TransactionsHandler();           // <— NEW (normalized)
            case "/transactions/list" -> new TransactionsHandler();      // <— NEW (alias)
            case "/createDeposit" -> new CreateDepositHandler();
            case "/deposit" -> new CreateDepositHandler();              // <— NEW (alias)
            case "/transfer" -> new TransferHandler();
            case "/withdraw" -> new WithdrawHandler();
            case "/createWithdraw" -> new WithdrawHandler();            // <— NEW (alias)

            case "/goals/create" -> new handler.goals.CreateGoalHandler();
            case "/goals/list" -> new handler.goals.ListGoalHandler();
            case "/goals/contribute" -> new handler.goals.ContributeGoalHandler();
            case "/goals/delete" -> new handler.goals.DeleteGoalHandler();
            case "/goals/transfer" -> new handler.goals.TransferGoalsHandler();
            case "/transferGoals" -> new handler.goals.TransferGoalsHandler(); // <— NEW (alias)

            case "/accounts/create" -> new handler.accounts.CreateAccountHandler();
            case "/accounts/list" -> new handler.accounts.ListAccountsHandler();
            case "/accounts/transfer" -> new handler.accounts.TransferBetweenAccountsHandler();
            case "/accounts/addFunds", "/accounts/deposit" -> new handler.accounts.AddFundsHandler(); // record income
            case "/accounts/listWithAllocations" -> new handler.accounts.ListAccountsWithAllocationsHandler(); // <— NEW
            case "/accounts/listDetailed" -> new handler.accounts.ListAccountsWithAllocationsHandler(); // <— NEW (alias)

            // Recurring monthly auto-contributions
            case "/routines/create" -> new handler.routines.CreateRoutineHandler();
            case "/routines/list" -> new handler.routines.ListRoutinesHandler();
            case "/routines/delete" -> new handler.routines.DeleteRoutineHandler();
            case "/routines/run" -> new handler.routines.RunRoutineHandler();

            // Plaid bank linking
            case "/plaid/create_link_token" -> new handler.plaid.CreateLinkTokenHandler();
            case "/plaid/exchange_public_token" -> new handler.plaid.ExchangePublicTokenHandler();
            case "/plaid/refresh" -> new handler.plaid.RefreshBalancesHandler();

            case "/auth/me" -> new WhoAmIHandler();
            case "/auth/profile" -> new WhoAmIHandler();
            case "/user/profile" -> new WhoAmIHandler();

            // Legacy generic transfer (not goal/account specific) left untouched
            default -> new FallbackHandler();
        };
    }

}
