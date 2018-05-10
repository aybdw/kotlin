/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.elements

import com.intellij.psi.*
import com.intellij.psi.impl.ResolveScopeManager
import org.jetbrains.kotlin.psi.*

class KtLightPsiArrayInitializerMemberValue(
    val ktOrigin: KtElement,
    val lightParent: PsiElement,
    val arguments: (KtLightPsiArrayInitializerMemberValue) -> List<PsiAnnotationMemberValue>
) : PsiElement by ktOrigin, PsiArrayInitializerMemberValue {
    override fun getInitializers(): Array<PsiAnnotationMemberValue> = arguments(this).toTypedArray()

    override fun getParent(): PsiElement = lightParent

    override fun isPhysical(): Boolean = false
}

class KtLightPsiLiteral(
    val ktOrigin: KtExpression,
    val lightParent: PsiElement
) : PsiElement by ktOrigin, PsiLiteralExpression {

    //TODO: maybe some evaluator?
    override fun getValue(): Any? = when (ktOrigin) {
        is KtStringTemplateExpression -> (ktOrigin.entries.single() as KtLiteralStringTemplateEntry).text // TODO: maybe not single
        else -> ktOrigin.text
    }

    override fun getType(): PsiType? {
        val manager = manager
        val resolveScope = ResolveScopeManager.getElementResolveScope(this)
        return PsiType.getJavaLangString(manager, resolveScope)
    }

    override fun getParent(): PsiElement = lightParent

    override fun isPhysical(): Boolean = false
}

class KtLightPsiNameValuePair private constructor(ktOrigin: KtElement, val valueArgument: KtValueArgument) : PsiElement by ktOrigin,
    PsiNameValuePair {

    constructor(valueArgument: KtValueArgument) : this(valueArgument.asElement(), valueArgument)


    override fun setValue(newValue: PsiAnnotationMemberValue): PsiAnnotationMemberValue {
        TODO("not implemented")
    }

    override fun getNameIdentifier(): PsiIdentifier? {
        TODO("not implemented")
    }

    override fun getName(): String? = valueArgument.name

    override fun getValue(): PsiAnnotationMemberValue? =
        valueArgument.getArgumentExpression()?.let { ktExpressionAsAnnotationMember(this, it) }

    override fun getLiteralValue(): String? = (getValue() as? PsiLiteralExpression)?.value?.toString()

}