package tm

package object syntax {
  object all
      extends RefinedSyntax
         with GenericSyntax
         with CirceSyntax
         with JavaTimeSyntax
         with OptionSyntax
         with ListSyntax

  object generic extends GenericSyntax
  object circe extends CirceSyntax
  object javaTime extends JavaTimeSyntax
  object refined extends RefinedSyntax
  object option extends OptionSyntax
  object list extends ListSyntax
}
